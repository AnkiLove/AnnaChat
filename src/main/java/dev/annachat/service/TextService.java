package dev.annachat.service;

import dev.annachat.AnnaChat;
import dev.annachat.api.PlaceholderProvider;
import dev.annachat.api.context.ChatContext;
import dev.annachat.config.TitleSettings;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextService {
    private static final LegacyComponentSerializer PLAYER_LEGACY_COLORS = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final Pattern CUSTOM_TOKEN = Pattern.compile("\\{custom:([a-zA-Z0-9_]+)}");
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("%[a-zA-Z0-9_:-]+%");
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"),
            Map.entry('2', "dark_green"), Map.entry('3', "dark_aqua"),
            Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"),
            Map.entry('8', "dark_gray"), Map.entry('9', "blue"),
            Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"),
            Map.entry('e', "yellow"), Map.entry('f', "white"),
            Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"),
            Map.entry('o', "italic"), Map.entry('r', "reset")
    );
    private static final Map<Character, String> LEGACY_PERMISSION_KEYS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark-blue"), Map.entry('2', "dark-green"),
            Map.entry('3', "dark-aqua"), Map.entry('4', "dark-red"), Map.entry('5', "dark-purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark-gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light-purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset")
    );
    private final AnnaChat plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, PlaceholderProvider> placeholders = new ConcurrentHashMap<>();
    private final boolean placeholderApiEnabled;
    private final LuckPermsGroupService groups = new LuckPermsGroupService();
    private volatile Map<String, String> customPlaceholders = Map.of();

    public TextService(AnnaChat plugin) {
        this.plugin = plugin;
        this.placeholderApiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public void register(PlaceholderProvider provider) {
        placeholders.put(provider.id().toLowerCase(Locale.ROOT), provider);
    }

    public void unregister(String id) {
        placeholders.remove(id.toLowerCase(Locale.ROOT));
    }

    public void applyCustomPlaceholders(Map<String, String> values) {
        customPlaceholders = Map.copyOf(values);
    }

    public Optional<String> customPlaceholder(String id) {
        return Optional.ofNullable(customPlaceholders.get(id.toLowerCase(Locale.ROOT)));
    }

    public String expand(ChatContext context, String input) {
        if (input == null || input.isEmpty()) return "";
        return expand(context, input, new HashSet<>(), 0, true);
    }

    private String expand(ChatContext context, String input, Set<String> resolving, int depth, boolean applyPapi) {
        if (input == null || input.isEmpty()) return "";
        String result = input;
        Map<String, String> builtins = Map.ofEntries(
                Map.entry("player", context.senderSnapshot().name()),
                Map.entry("name", context.senderSnapshot().name()),
                Map.entry("display_name", context.senderSnapshot().displayName()),
                Map.entry("displayname", context.senderSnapshot().displayName()),
                Map.entry("nickname", context.senderSnapshot().displayName()),
                Map.entry("title", resolveTitle(context)),
                Map.entry("group", groups.primaryGroup(context.sender()).orElse("default")),
                Map.entry("message", context.message()),
                Map.entry("uuid", context.senderSnapshot().uniqueId().toString()),
                Map.entry("world", context.senderSnapshot().worldKey().asString()),
                Map.entry("x", Integer.toString((int) Math.floor(context.senderSnapshot().x()))),
                Map.entry("y", Integer.toString((int) Math.floor(context.senderSnapshot().y()))),
                Map.entry("z", Integer.toString((int) Math.floor(context.senderSnapshot().z()))),
                Map.entry("channel", context.channel().id()),
                Map.entry("channel_display", context.channel().displayName()),
                Map.entry("radius", Long.toString(Math.round(context.channel().radius()))),
                Map.entry("online", Integer.toString(plugin.onlinePlayers().count()))
        );
        for (Map.Entry<String, String> entry : builtins.entrySet()) {
            // 称号由管理员配置，允许其中的 MiniMessage/传统颜色码参与最终渲染；
            // 玩家名称、世界名等外部数据仍必须转义，避免注入标签。
            String replacement = entry.getKey().equals("title")
                    ? entry.getValue()
                    : MiniMessage.miniMessage().escapeTags(entry.getValue());
            result = result.replace("{" + entry.getKey() + "}", replacement);
        }
        for (Map.Entry<String, PlaceholderProvider> entry : placeholders.entrySet()) {
            String token = "{" + entry.getKey() + "}";
            if (result.contains(token)) {
                String value = Objects.toString(entry.getValue().resolve(context), "");
                result = result.replace(token, miniMessage.escapeTags(value));
            }
        }
        if (depth < 10) {
            Matcher matcher = CUSTOM_TOKEN.matcher(result);
            StringBuffer output = new StringBuffer();
            while (matcher.find()) {
                String id = matcher.group(1).toLowerCase(Locale.ROOT);
                String replacement = "";
                String template = customPlaceholders.get(id);
                if (template != null && resolving.add(id)) {
                    replacement = expand(context, template, resolving, depth + 1, false);
                    resolving.remove(id);
                }
                matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(output);
            result = output.toString();
        }
        if (applyPapi && placeholderApiEnabled) {
            result = PlaceholderAPI.setPlaceholders(context.sender(), result);
            if (plugin.runtime().stripUnresolvedPlaceholders()) {
                result = UNRESOLVED_PLACEHOLDER.matcher(result).replaceAll("");
            }
        }
        return result;
    }

    public String expandForPlayer(Player player, String input) {
        String channelId = plugin.state().channel(player.getUniqueId(), plugin.runtime().defaultChannel());
        var channel = plugin.channels().find(channelId)
                .orElseGet(() -> plugin.channels().find(plugin.runtime().defaultChannel()).orElseThrow());
        ChatContext context = new ChatContext(player, dev.annachat.api.context.PlayerSnapshot.capture(player), channel, "");
        return expand(context, input);
    }

    /**
     * 解析不带玩家上下文的文本，供控制台、计分板和全局 PlaceholderAPI 调用使用。
     *
     * <p>自定义变量、在线人数及可接受空玩家的 PAPI 变量仍会正常解析；必须依赖
     * 玩家位置或频道的内置变量会变为空字符串，避免把未解析的花括号原样泄露到
     * 展示内容中。</p>
     */
    public String expandWithoutPlayer(String input) {
        String result = expandCustomWithoutPlayer(input, new HashSet<>(), 0);
        result = result.replace("{online}", Integer.toString(plugin.onlinePlayers().count()));
        for (String token : List.of(
                "player", "name", "display_name", "displayname", "nickname", "title", "group", "message", "uuid", "world", "x", "y", "z",
                "channel", "channel_display", "radius")) {
            result = result.replace("{" + token + "}", "");
        }
        if (placeholderApiEnabled) {
            result = PlaceholderAPI.setPlaceholders((OfflinePlayer) null, result);
            if (plugin.runtime().stripUnresolvedPlaceholders()) {
                result = UNRESOLVED_PLACEHOLDER.matcher(result).replaceAll("");
            }
        }
        return result;
    }

    private String expandCustomWithoutPlayer(String input, Set<String> resolving, int depth) {
        if (input == null || input.isEmpty() || depth >= 10) return input == null ? "" : input;
        Matcher matcher = CUSTOM_TOKEN.matcher(input);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String id = matcher.group(1).toLowerCase(Locale.ROOT);
            String replacement = "";
            String template = customPlaceholders.get(id);
            if (template != null && resolving.add(id)) {
                replacement = expandCustomWithoutPlayer(template, resolving, depth + 1);
                resolving.remove(id);
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    public Component configured(ChatContext context, String input) {
        return miniMessage.deserialize(configuredMiniMessageSafe(expand(context, input)));
    }

    public Component playerText(ChatContext context, String input, String style) {
        if (placeholderApiEnabled
                && context.sender().hasPermission(plugin.runtime().playerMessagePlaceholdersPermission())) {
            input = PlaceholderAPI.setPlaceholders(context.sender(), input);
            if (plugin.runtime().stripUnresolvedPlaceholders()) {
                input = UNRESOLVED_PLACEHOLDER.matcher(input).replaceAll("");
            }
        }
        Component body;
        String globalColorPermission = plugin.runtime().legacyColorPermission();
        boolean legacyColors = globalColorPermission != null && !globalColorPermission.isBlank()
                && context.sender().hasPermission(globalColorPermission);
        boolean anyLegacyPermission = legacyColors || hasAnyLegacyPermission(context.sender());
        if (context.sender().hasPermission(plugin.runtime().miniMessagePermission())) {
            // OP 默认同时拥有两项权限，因此 MiniMessage 与 & 颜色代码必须能够共存。
            body = miniMessage.deserialize(playerMiniMessageSafe(input, context.sender(), legacyColors));
        } else if (anyLegacyPermission) {
            body = legacyPlayerText(stripUnauthorizedLegacyCodes(input, context.sender(), legacyColors));
        } else {
            body = Component.text(input);
        }
        if (style == null || style.isBlank()) return body;
        return miniMessage.deserialize(miniMessageSafe(style) + "<message>",
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("message", body));
    }

    public Component configuredComponent(ChatContext context, String text, List<String> hover,
                                         String clickAction, String clickValue, String insertion) {
        Component result = configured(context, text);
        if (hover != null && !hover.isEmpty()) {
            Component hoverComponent = Component.empty();
            boolean first = true;
            for (String line : hover) {
                if (!first) hoverComponent = hoverComponent.append(Component.newline());
                hoverComponent = hoverComponent.append(configured(context, line));
                first = false;
            }
            result = result.hoverEvent(hoverComponent);
        }
        String action = clickAction == null ? "" : clickAction.toUpperCase(Locale.ROOT);
        String value = expand(context, clickValue);
        if (!action.isBlank() && !value.isBlank()) {
            result = switch (action) {
                case "RUN_COMMAND" -> result.clickEvent(ClickEvent.runCommand(value));
                case "SUGGEST_COMMAND" -> result.clickEvent(ClickEvent.suggestCommand(value));
                case "OPEN_URL" -> result.clickEvent(ClickEvent.openUrl(value));
                case "COPY_TO_CLIPBOARD" -> result.clickEvent(ClickEvent.copyToClipboard(value));
                default -> throw new IllegalArgumentException("不支持的点击动作: " + action);
            };
        }
        String expandedInsertion = expand(context, insertion);
        if (!expandedInsertion.isBlank()) result = result.insertion(expandedInsertion);
        return result;
    }

    public Component parseRaw(String miniMessageText) {
        return miniMessage.deserialize(configuredMiniMessageSafe(miniMessageText));
    }

    /**
     * 配置渲染专用：把传统 {@code &} / {@code §} 颜色码转换为 MiniMessage 标签。
     *
     * <p>此方法只供 formats.yml、config.yml 等受管理员控制的配置文本调用，不影响
     * 玩家原始聊天正文，也不改变点击命令或插入文本。支持 0-9、a-f、k-o、r，
     * 以及 {@code &x&F&F&0&0&A&A} / {@code §x§F§F§0§0§A§A} 十六进制格式。</p>
     */
    public String configuredMiniMessageSafe(String input) {
        return miniMessageSafe(input, true);
    }

    /**
     * 玩家允许使用 MiniMessage 时的安全预处理：仅兼容第三方占位符中的 {@code §}
     * 颜色码，不把普通聊天中的 {@code &} 当作颜色指令。
     */
    public String miniMessageSafe(String input) {
        return miniMessageSafe(input, false);
    }

    private boolean hasAnyLegacyPermission(Player player) {
        return plugin.runtime().legacyColorPermissions().values().stream().anyMatch(player::hasPermission)
                || player.hasPermission(plugin.runtime().hexColorPermission());
    }

    private boolean allowsLegacy(Player player, boolean global, char code, boolean hex) {
        if (global) return true;
        if (hex) return player.hasPermission(plugin.runtime().hexColorPermission());
        String key = LEGACY_PERMISSION_KEYS.get(Character.toLowerCase(code));
        if (key == null) return true;
        String permission = plugin.runtime().legacyColorPermissions().get(key);
        return permission != null && !permission.isBlank() && player.hasPermission(permission);
    }

    private String stripUnauthorizedLegacyCodes(String input, Player player, boolean global) {
        if (input == null || input.isEmpty()) return "";
        StringBuilder output = new StringBuilder(input.length());
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if ((current != '&' && current != '§') || index + 1 >= input.length()) {
                output.append(current);
                continue;
            }
            char code = Character.toLowerCase(input.charAt(index + 1));
            if (code == 'x') {
                String hex = readLegacyHex(input, index, current);
                if (hex != null) {
                    if (allowsLegacy(player, global, code, true)) output.append(input, index, index + 14);
                    index += 13;
                    continue;
                }
            }
            if (LEGACY_TAGS.containsKey(code)) {
                if (allowsLegacy(player, global, code, false)) output.append(current).append(input.charAt(index + 1));
                index++;
                continue;
            }
            output.append(current);
        }
        return output.toString();
    }

    private static String miniMessageSafe(String input, boolean allowAmpersand) {
        return miniMessageSafe(input, allowAmpersand, (code, hex) -> true);
    }

    private static String miniMessageSafe(String input, boolean allowAmpersand, LegacyCodePolicy policy) {
        if (input == null || input.isEmpty()
                || (input.indexOf('§') < 0 && (!allowAmpersand || input.indexOf('&') < 0))) {
            return input == null ? "" : input;
        }
        StringBuilder output = new StringBuilder(input.length() + 16);
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if ((current != '§' && (!allowAmpersand || current != '&')) || index + 1 >= input.length()) {
                output.append(current);
                continue;
            }

            char code = Character.toLowerCase(input.charAt(index + 1));
            if (code == 'x') {
                String hex = readLegacyHex(input, index, current);
                if (hex != null) {
                    if (policy.allowed('x', true)) output.append("<reset><#").append(hex).append('>');
                    index += 13;
                    continue;
                }
            }

            String tag = LEGACY_TAGS.get(code);
            if (tag == null) {
                // 未知代码按普通文本保留，便于管理员发现上游占位符的异常输出。
                output.append(current);
                continue;
            }
            if (policy.allowed(code, false)) {
                if (isLegacyColor(code)) output.append("<reset>");
                output.append('<').append(tag).append('>');
            }
            index++;
        }
        return output.toString();
    }

    private static String readLegacyHex(String input, int start, char markerCharacter) {
        if (start + 13 >= input.length()) return null;
        StringBuilder hex = new StringBuilder(6);
        for (int pair = 0; pair < 6; pair++) {
            int marker = start + 2 + pair * 2;
            if (input.charAt(marker) != markerCharacter) return null;
            char digit = input.charAt(marker + 1);
            if (Character.digit(digit, 16) < 0) return null;
            hex.append(digit);
        }
        return hex.toString();
    }

    private static boolean isLegacyColor(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    public String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /**
     * 支持 &a、&l、&#RRGGBB、&x&R&R&G&G&B&B 以及对应的 § 形式。
     */
    static Component legacyPlayerText(String input) {
        return PLAYER_LEGACY_COLORS.deserialize(input.replace('§', '&'));
    }

    static String playerMiniMessageSafe(String input, boolean legacyColors) {
        return miniMessageSafe(input, legacyColors);
    }

    private String playerMiniMessageSafe(String input, Player player, boolean global) {
        return miniMessageSafe(input, true, (code, hex) -> allowsLegacy(player, global, code, hex));
    }

    private String resolveTitle(ChatContext context) {
        TitleSettings settings = plugin.runtime().titles();
        if (!settings.enabled()) return "";
        for (TitleSettings.TitleRule rule : settings.rules()) {
            if (!rule.permission().isBlank() && !context.sender().hasPermission(rule.permission())) continue;
            if (!rule.group().isBlank()
                    && !groups.primaryGroup(context.sender()).map(group -> group.equalsIgnoreCase(rule.group())).orElse(false)) {
                continue;
            }
            return rule.value();
        }
        return settings.defaultValue();
    }

    @FunctionalInterface
    private interface LegacyCodePolicy {
        boolean allowed(char code, boolean hex);
    }
}
