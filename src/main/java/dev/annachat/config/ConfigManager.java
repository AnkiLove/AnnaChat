package dev.annachat.config;

import dev.annachat.AnnaChat;
import dev.annachat.api.AudienceType;
import dev.annachat.api.FilterResult;
import dev.annachat.service.TextService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventPriority;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ConfigManager {
    private static final List<String> FILES = List.of(
            "channels.yml", "formats.yml", "interactions.yml", "filters.yml", "messages.yml",
            "moderation.yml", "placeholders.yml", "database.yml"
    );

    private final AnnaChat plugin;

    public ConfigManager(AnnaChat plugin) {
        this.plugin = plugin;
    }

    public void ensureDefaults() {
        plugin.saveDefaultConfig();
        for (String file : FILES) {
            if (!new File(plugin.getDataFolder(), file).isFile()) {
                plugin.saveResource(file, false);
            }
        }
        migrateLegacyChannelDefaults();
    }

    /**
     * 迁移内置频道与格式：1.0.8 精简为全服、附近和好友，1.1.0 将旧默认格式
     * 改为传统 & 颜色码。两类迁移均只处理已知旧值，避免覆盖自定义配置。
     */
    private void migrateLegacyChannelDefaults() {
        File channelFile = new File(plugin.getDataFolder(), "channels.yml");
        File formatFile = new File(plugin.getDataFolder(), "formats.yml");
        File mainFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration channels = YamlConfiguration.loadConfiguration(channelFile);
        YamlConfiguration formats = YamlConfiguration.loadConfiguration(formatFile);
        YamlConfiguration main = YamlConfiguration.loadConfiguration(mainFile);
        boolean oldChannels = channels.getInt("schema-version", 1) < 2;
        boolean oldFormats = formats.getInt("schema-version", 1) < 2;
        boolean oldQuickSwitch = "staff".equalsIgnoreCase(main.getString("quick-switch.#", ""));
        boolean legacyChannelLayout = oldChannels || oldFormats || oldQuickSwitch;
        boolean oldFormatColors = formats.getInt("schema-version", 1) < 4;
        if (!legacyChannelLayout && !oldFormatColors) return;

        if (legacyChannelLayout) {
            backupOnce(channelFile, "channels.yml.pre-1.0.8.bak");
            backupOnce(formatFile, "formats.yml.pre-1.0.8.bak");
            backupOnce(mainFile, "config.yml.pre-1.0.8.bak");
        }
        if (oldFormatColors) backupOnce(formatFile, "formats.yml.pre-1.1.0.bak");

        if (legacyChannelLayout) {
            YamlConfiguration bundledChannels = loadBundled("channels.yml");
            channels.set("channels.world", null);
            channels.set("channels.staff", null);
            if (!channels.isConfigurationSection("channels.friends")) {
                copySection(bundledChannels, channels, "channels.friends");
            }
            channels.set("schema-version", 2);

            YamlConfiguration bundledFormats = loadBundled("formats.yml");
            formats.set("formats.world", null);
            formats.set("formats.staff", null);
            if (!formats.isConfigurationSection("formats.friends")) {
                copySection(bundledFormats, formats, "formats.friends");
            }

            if ("staff".equalsIgnoreCase(main.getString("quick-switch.#", ""))) {
                main.set("quick-switch.#", "friends");
            }
        }
        if (oldFormatColors) {
            migrateDefaultFormatColors(formats);
        }
        formats.set("schema-version", 4);
        try {
            channels.save(channelFile);
            formats.save(formatFile);
            main.save(mainFile);
            plugin.getLogger().info(legacyChannelLayout
                    ? "已将旧频道配置迁移为全服、附近和好友频道；原文件已备份"
                    : "已将旧默认聊天格式迁移为 & 颜色码；原格式文件已备份");
        } catch (IOException exception) {
            throw new IllegalStateException("保存频道或格式迁移配置失败", exception);
        }
    }

    /**
     * 只替换 1.0.8 的原始默认值。管理员已经修改过的格式会保留，避免升级时
     * 意外重写服务器的视觉配置。
     */
    private static void migrateDefaultFormatColors(YamlConfiguration formats) {
        replaceExactInFormatPart(formats, "global", 0,
                "<dark_gray>[<aqua>{channel_display}</aqua>]</dark_gray> ",
                "&8[<aqua>{channel_display}</aqua>&8] ");
        replaceExactInFormatPart(formats, "global", 2, "<dark_gray> » </dark_gray>", "&8 » ");
        replaceExactInFormatPart(formats, "local", 0,
                "<dark_gray>[<yellow>{channel_display}</yellow>]</dark_gray> ",
                "&8[<yellow>{channel_display}</yellow>&8] ");
        replaceExactInFormatPart(formats, "local", 1,
                "<white>{player}</white><dark_gray> » </dark_gray>", "<white>{player}</white>&8 » ");
        replaceExactInFormatPart(formats, "friends", 0,
                "<dark_gray>[<#FFB7C5>好友</#FFB7C5>]</dark_gray> ",
                "&8[<#FFB7C5>好友</#FFB7C5>&8] ");
        replaceExactInFormatPart(formats, "friends", 1,
                "<white>{player}</white><dark_gray> » </dark_gray>", "<white>{player}</white>&8 » ");
    }

    private static void replaceExactInFormatPart(YamlConfiguration configuration, String formatId, int partIndex,
                                                 String oldValue, String newValue) {
        String path = "formats." + formatId + ".parts";
        List<Map<?, ?>> rawParts = configuration.getMapList(path);
        if (partIndex < 0 || partIndex >= rawParts.size() || !oldValue.equals(rawParts.get(partIndex).get("content"))) {
            return;
        }
        List<Map<String, Object>> updatedParts = new ArrayList<>();
        for (Map<?, ?> rawPart : rawParts) {
            Map<String, Object> copy = new LinkedHashMap<>();
            rawPart.forEach((key, value) -> copy.put(String.valueOf(key), value));
            updatedParts.add(copy);
        }
        updatedParts.get(partIndex).put("content", newValue);
        configuration.set(path, updatedParts);
    }

    private YamlConfiguration loadBundled(String name) {
        try (InputStream input = Objects.requireNonNull(plugin.getResource(name), "JAR 内缺少 " + name);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("读取 JAR 内默认 " + name + " 失败", exception);
        }
    }

    private static void copySection(YamlConfiguration source, YamlConfiguration target, String path) {
        ConfigurationSection section = requiredSection(source, path);
        for (String key : section.getKeys(true)) {
            String sourcePath = path + "." + key;
            if (!source.isConfigurationSection(sourcePath)) target.set(sourcePath, source.get(sourcePath));
        }
    }

    private void backupOnce(File source, String backupName) {
        if (!source.isFile()) return;
        File backup = new File(plugin.getDataFolder(), backupName);
        if (backup.isFile()) return;
        try {
            Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException exception) {
            throw new IllegalStateException("备份旧配置失败: " + source.getName(), exception);
        }
    }

    public RuntimeConfig load(TextService textService) {
        YamlConfiguration mainFile = loadFile("config.yml");
        YamlConfiguration channelsFile = loadFile("channels.yml");
        YamlConfiguration formatsFile = loadFile("formats.yml");
        YamlConfiguration interactionsFile = loadFile("interactions.yml");
        YamlConfiguration filtersFile = loadFile("filters.yml");
        YamlConfiguration moderationFile = loadFile("moderation.yml");
        YamlConfiguration messagesFile = loadFile("messages.yml");
        YamlConfiguration placeholdersFile = loadFile("placeholders.yml");
        YamlConfiguration databaseFile = loadFile("database.yml");

        Map<String, ConfiguredChannel> channels = loadChannels(channelsFile);
        Map<String, FormatDefinition> formats = loadFormats(formatsFile);
        for (ConfiguredChannel channel : channels.values()) {
            if (!formats.containsKey(channel.formatId().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("频道 " + channel.id() + " 引用了不存在的格式 " + channel.formatId());
            }
        }

        Map<String, String> switches = new LinkedHashMap<>();
        ConfigurationSection switchSection = mainFile.getConfigurationSection("quick-switch");
        if (switchSection != null) {
            for (String prefix : switchSection.getKeys(false)) {
                if (prefix.isEmpty()) throw new IllegalArgumentException("快捷切换前缀不能为空");
                String channel = Objects.requireNonNull(switchSection.getString(prefix)).toLowerCase(Locale.ROOT);
                if (!channels.containsKey(channel)) {
                    throw new IllegalArgumentException("快捷前缀 " + prefix + " 引用了不存在的频道 " + channel);
                }
                switches.put(prefix, channel);
            }
        }

        String defaultChannel = mainFile.getString("settings.default-channel", "global").toLowerCase(Locale.ROOT);
        if (!channels.containsKey(defaultChannel)) {
            throw new IllegalArgumentException("默认频道不存在: " + defaultChannel);
        }
        EventPriority priority;
        try {
            priority = EventPriority.valueOf(mainFile.getString("settings.chat-event-priority", "HIGHEST").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("无效聊天事件优先级", exception);
        }

        Map<String, String> messages = new HashMap<>();
        ConfigurationSection messageSection = mainFile.getConfigurationSection("messages");
        if (messageSection != null) {
            for (String key : messageSection.getKeys(false)) {
                messages.put(key, messageSection.getString(key, ""));
            }
        }

        Map<String, String> customPlaceholders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        ConfigurationSection customSection = placeholdersFile.getConfigurationSection("custom-placeholders");
        if (customSection != null) {
            for (String key : customSection.getKeys(false)) {
                String normalized = key.toLowerCase(Locale.ROOT);
                if (!normalized.matches("[a-z0-9_]+")) {
                    throw new IllegalArgumentException("自定义占位符键名只能包含小写字母、数字和下划线: " + key);
                }
                customPlaceholders.put(normalized, customSection.getString(key, ""));
            }
        }

        return new RuntimeConfig(
                Map.copyOf(channels),
                Map.copyOf(formats),
                loadInteractions(interactionsFile, textService),
                loadFilters(filtersFile),
                loadModeration(moderationFile),
                Map.copyOf(switches),
                defaultChannel,
                priority,
                Math.max(10, mainFile.getInt("settings.history-size", 200)),
                Math.max(1, mainFile.getInt("settings.max-message-length", 256)),
                Math.max(0, mainFile.getLong("settings.default-cooldown-millis", 1000)),
                Math.max(30, mainFile.getLong("storage.autosave-seconds", 120)),
                mainFile.getBoolean("settings.cancel-when-no-channel", true),
                mainFile.getBoolean("settings.notify-when-no-other-recipients", false),
                mainFile.getBoolean("settings.log-chat", true),
                mainFile.getString("formatting.legacy-color-permission", "annachat.chat.color"),
                mainFile.getString("formatting.minimessage-permission", "annachat.chat.minimessage"),
                mainFile.getString("formatting.player-message-placeholders-permission", "annachat.chat.placeholders"),
                Map.copyOf(customPlaceholders),
                Map.copyOf(messages),
                messagesFile,
                databaseFile
        );
    }

    private YamlConfiguration loadFile(String name) {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));
        if (name.equals("config.yml")) {
            mergeMainConfigDefaults(configuration);
        }
        return configuration;
    }

    /**
     * 把新版本主配置中新增的运行选项补入内存副本，但不改写用户磁盘文件。
     *
     * <p>只合并固定的系统配置区，刻意不合并 {@code quick-switch} 等用户可自由
     * 删除的集合，避免升级后重新出现管理员已经移除的快捷前缀。用户已有值始终
     * 优先；缺失值则使用当前 JAR 内的默认值。</p>
     */
    private void mergeMainConfigDefaults(YamlConfiguration target) {
        try (InputStream input = Objects.requireNonNull(
                plugin.getResource("config.yml"), "JAR 内缺少 config.yml");
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            Set<String> mergeRoots = Set.of("settings", "formatting", "storage", "messages");
            for (String path : defaults.getKeys(true)) {
                String root = path.contains(".") ? path.substring(0, path.indexOf('.')) : path;
                if (!mergeRoots.contains(root) || defaults.isConfigurationSection(path) || target.contains(path)) {
                    continue;
                }
                target.set(path, defaults.get(path));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("读取 JAR 内默认 config.yml 失败", exception);
        }
    }

    private Map<String, ConfiguredChannel> loadChannels(YamlConfiguration file) {
        ConfigurationSection root = requiredSection(file, "channels");
        Map<String, ConfiguredChannel> channels = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String rawId : root.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            ConfigurationSection section = requiredSection(root, rawId);
            AudienceType audience = AudienceType.valueOf(section.getString("audience", "GLOBAL").toUpperCase(Locale.ROOT));
            String permission = section.getString("permission", "");
            String receivePermission = section.getString("receive-permission", "");
            channels.put(id, new ConfiguredChannel(
                    id,
                    section.getString("display-name", id),
                    section.getString("format", id).toLowerCase(Locale.ROOT),
                    permission == null ? "" : permission,
                    receivePermission == null ? "" : receivePermission,
                    audience,
                    Math.max(0, section.getDouble("radius", 0)),
                    section.getBoolean("same-world", true),
                    Math.max(0, section.getLong("cooldown-millis", -1)),
                    section.getInt("priority", 1000),
                    section.getBoolean("enabled", true)
            ));
        }
        if (channels.isEmpty()) throw new IllegalArgumentException("至少需要配置一个频道");
        return channels;
    }

    private Map<String, FormatDefinition> loadFormats(YamlConfiguration file) {
        ConfigurationSection root = requiredSection(file, "formats");
        Map<String, FormatDefinition> formats = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String rawId : root.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            List<Map<?, ?>> rawParts = root.getMapList(rawId + ".parts");
            if (rawParts.isEmpty()) throw new IllegalArgumentException("格式 " + id + " 没有任何片段");
            List<FormatDefinition.Part> parts = new ArrayList<>();
            int index = 0;
            for (Map<?, ?> raw : rawParts) {
                YamlConfiguration partConfig = new YamlConfiguration();
                for (Map.Entry<?, ?> entry : raw.entrySet()) {
                    copyValue(partConfig, String.valueOf(entry.getKey()), entry.getValue());
                }
                if (raw.containsKey("click")) {
                    String action = partConfig.getString("click.action", "");
                    String value = partConfig.getString("click.value", "");
                    if (action.isBlank() || value.isBlank()) {
                        throw new IllegalArgumentException("格式 " + id + " 的第 " + (index + 1) + " 个片段点击事件缺少 action 或 value");
                    }
                    if (!Set.of("RUN_COMMAND", "SUGGEST_COMMAND", "OPEN_URL", "COPY_TO_CLIPBOARD")
                            .contains(action.toUpperCase(Locale.ROOT))) {
                        throw new IllegalArgumentException("格式 " + id + " 的第 " + (index + 1) + " 个片段点击动作无效: " + action);
                    }
                }
                String type = partConfig.getString("type", "text").toLowerCase(Locale.ROOT);
                parts.add(new FormatDefinition.Part(type, partConfig));
                index++;
            }
            formats.put(id, new FormatDefinition(id, List.copyOf(parts)));
        }
        return formats;
    }

    private static void copyValue(ConfigurationSection target, String path, Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copyValue(target, path + "." + entry.getKey(), entry.getValue());
            }
            return;
        }
        target.set(path, value);
    }

    private List<ConfiguredInteraction> loadInteractions(YamlConfiguration file, TextService textService) {
        ConfigurationSection root = file.getConfigurationSection("interactions");
        if (root == null) return List.of();
        List<ConfiguredInteraction> result = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = requiredSection(root, id);
            if (!section.getBoolean("enabled", true)) continue;
            try {
                Pattern pattern = Pattern.compile(Objects.requireNonNull(section.getString("pattern"), "缺少 pattern"));
                ConfigurationSection click = section.getConfigurationSection("click");
                result.add(new ConfiguredInteraction(
                        section.getInt("priority", 1000),
                        pattern,
                        section.getString("text", "<white>{match}</white>"),
                        section.getStringList("hover"),
                        click == null ? "" : click.getString("action", ""),
                        click == null ? "" : click.getString("value", ""),
                        section.getString("insertion", ""),
                        textService
                ));
            } catch (PatternSyntaxException exception) {
                throw new IllegalArgumentException("交互规则 " + id + " 的正则表达式无效", exception);
            }
        }
        result.sort(Comparator.comparingInt(ConfiguredInteraction::priority));
        return List.copyOf(result);
    }

    private List<ConfiguredFilter> loadFilters(YamlConfiguration file) {
        ConfigurationSection root = file.getConfigurationSection("filters");
        if (root == null) return List.of();
        List<ConfiguredFilter> result = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = requiredSection(root, id);
            if (!section.getBoolean("enabled", true)) continue;
            try {
                result.add(new ConfiguredFilter(
                        section.getInt("priority", 1000),
                        ConfiguredFilter.Type.valueOf(section.getString("type", "CONTAINS").toUpperCase(Locale.ROOT)),
                        Objects.requireNonNull(section.getString("pattern"), "缺少 pattern"),
                        section.getBoolean("ignore-case", false),
                        FilterResult.Action.valueOf(section.getString("action", "BLOCK").toUpperCase(Locale.ROOT)),
                        section.getString("replacement", ""),
                        section.getString("reason", id)
                ));
            } catch (PatternSyntaxException exception) {
                throw new IllegalArgumentException("过滤规则 " + id + " 的正则表达式无效", exception);
            }
        }
        result.sort(Comparator.comparingInt(ConfiguredFilter::priority));
        return List.copyOf(result);
    }

    /**
     * 加载分类审核词库。词条只在热重载阶段读取，聊天线程不会访问 YAML。
     */
    private ModerationSettings loadModeration(YamlConfiguration file) {
        Set<Integer> ignoredCodePoints = new HashSet<>();
        for (String value : file.getStringList("matching.ignored-characters")) {
            value.codePoints().forEach(ignoredCodePoints::add);
        }

        ConfigurationSection root = file.getConfigurationSection("categories");
        List<ModerationSettings.Category> categories = new ArrayList<>();
        if (root != null) {
            for (String rawId : root.getKeys(false)) {
                ConfigurationSection section = requiredSection(root, rawId);
                if (!section.getBoolean("enabled", true)) continue;
                String id = rawId.toLowerCase(Locale.ROOT);
                if (!id.matches("[a-z0-9_-]+")) {
                    throw new IllegalArgumentException("审核分类 ID 只能包含小写字母、数字、下划线和连字符: " + rawId);
                }
                List<String> words = cleanStringList(section.getStringList("words"));
                if (words.isEmpty()) {
                    plugin.getLogger().warning("审核分类 " + id + " 没有词条，已跳过");
                    continue;
                }
                categories.add(new ModerationSettings.Category(
                        id,
                        section.getString("display-name", id),
                        section.getInt("priority", 1000),
                        section.getString("reason", "消息包含不允许发布的内容"),
                        words,
                        cleanStringList(section.getStringList("whitelist"))
                ));
            }
        }
        categories.sort(Comparator.comparingInt(ModerationSettings.Category::priority));
        return new ModerationSettings(
                file.getBoolean("enabled", true),
                file.getString("bypass-permission", "annachat.bypass.moderation"),
                file.getBoolean("matching.ignore-whitespace", true),
                file.getBoolean("matching.ignore-punctuation", true),
                Set.copyOf(ignoredCodePoints),
                Math.max(1, file.getInt("warnings.reset-after-seconds", 300)),
                file.getBoolean("warnings.console-notify", true),
                List.copyOf(categories)
        );
    }

    private static List<String> cleanStringList(List<String> values) {
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) cleaned.add(value.strip());
        }
        return List.copyOf(cleaned);
    }

    private static ConfigurationSection requiredSection(ConfigurationSection parent, String path) {
        ConfigurationSection section = parent.getConfigurationSection(path);
        if (section == null) throw new IllegalArgumentException("缺少配置节点: " + path);
        return section;
    }
}
