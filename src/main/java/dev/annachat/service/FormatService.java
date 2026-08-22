package dev.annachat.service;

import dev.annachat.api.FormatPartProvider;
import dev.annachat.api.context.ChatContext;
import dev.annachat.config.FormatDefinition;
import dev.annachat.config.FormatRule;
import net.kyori.adventure.text.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class FormatService {
    private final TextService textService;
    private final InteractionService interactionService;
    private volatile Map<String, FormatDefinition> formats = Map.of();
    private volatile Map<String, java.util.List<FormatRule>> rules = Map.of();
    private final Map<String, FormatPartProvider> providers = new ConcurrentHashMap<>();
    private final LuckPermsGroupService groups = new LuckPermsGroupService();

    public FormatService(TextService textService, InteractionService interactionService) {
        this.textService = textService;
        this.interactionService = interactionService;
        register("text", (context, config) -> textService.configuredComponent(
                context,
                content(config),
                config.getStringList("hover"),
                config.getString("click.action", ""),
                config.getString("click.value", ""),
                config.getString("insertion", "")
        ));
        register("message", (context, config) -> interactionService.render(context, config.getString("style", "")));
    }

    public void apply(Map<String, FormatDefinition> formats) {
        apply(formats, java.util.List.of());
    }

    public void apply(Map<String, FormatDefinition> formats, java.util.List<FormatRule> formatRules) {
        for (FormatDefinition format : formats.values()) {
            for (FormatDefinition.Part part : format.parts()) {
                if (!providers.containsKey(part.type())) {
                    throw new IllegalArgumentException("格式 " + format.id() + " 使用了未注册的片段类型 " + part.type());
                }
            }
        }
        Map<String, java.util.List<FormatRule>> grouped = new java.util.HashMap<>();
        for (FormatRule rule : formatRules) {
            if (!formats.containsKey(rule.format())) {
                throw new IllegalArgumentException("格式规则引用了不存在的格式: " + rule.format());
            }
            if (rule.baseFormat().isBlank() || !formats.containsKey(rule.baseFormat())) {
                throw new IllegalArgumentException("格式规则的 base-format 不存在: " + rule.baseFormat());
            }
            if (rule.permission().isBlank() && rule.group().isBlank()) {
                throw new IllegalArgumentException("格式规则必须设置 permission 或 group");
            }
            grouped.computeIfAbsent(rule.baseFormat(), ignored -> new java.util.ArrayList<>()).add(rule);
        }
        grouped.values().forEach(list -> list.sort(java.util.Comparator.comparingInt(FormatRule::priority)));
        Map<String, java.util.List<FormatRule>> immutable = new java.util.HashMap<>();
        grouped.forEach((key, value) -> immutable.put(key, java.util.List.copyOf(value)));
        this.formats = Map.copyOf(formats);
        this.rules = Map.copyOf(immutable);
    }

    public void register(String type, FormatPartProvider provider) {
        providers.put(normalize(type), Objects.requireNonNull(provider));
    }

    public void unregister(String type) {
        String key = normalize(type);
        if (key.equals("text") || key.equals("message")) {
            throw new IllegalArgumentException("不能注销内置格式片段");
        }
        providers.remove(key);
    }

    public Component render(ChatContext context) {
        return render(context, context.channel().formatId());
    }

    public Component render(ChatContext context, String formatId) {
        String baseId = formatId.toLowerCase(Locale.ROOT);
        String selectedId = selectFormat(context, baseId);
        FormatDefinition format = formats.get(selectedId);
        if (format == null) throw new IllegalStateException("找不到聊天格式 " + context.channel().formatId());
        Component result = Component.empty();
        for (int index = 0; index < format.parts().size(); index++) {
            FormatDefinition.Part part = format.parts().get(index);
            FormatPartProvider provider = providers.get(part.type());
            if (provider == null) throw new IllegalStateException("找不到格式片段提供器 " + part.type());
            if (index > 0 && !format.partSeparator().isBlank()) {
                result = result.append(textService.configured(context, format.partSeparator()));
            }
            result = result.append(provider.render(context, part.configuration()));
        }
        return result;
    }

    private static String content(org.bukkit.configuration.ConfigurationSection config) {
        if (config.isList("content")) return String.join("\n", config.getStringList("content"));
        if (config.contains("content")) return config.getString("content", "");
        if (config.isList("text")) return String.join("\n", config.getStringList("text"));
        return config.getString("text", "");
    }

    private String selectFormat(ChatContext context, String baseId) {
        for (FormatRule rule : rules.getOrDefault(baseId, java.util.List.of())) {
            if (!rule.permission().isBlank() && !context.sender().hasPermission(rule.permission())) continue;
            if (!rule.group().isBlank()
                    && !groups.primaryGroup(context.sender())
                    .map(group -> group.equalsIgnoreCase(rule.group())).orElse(false)) {
                continue;
            }
            return rule.format();
        }
        return baseId;
    }

    public int count() {
        return formats.size();
    }

    public boolean contains(String id) {
        return formats.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public java.util.Set<String> ids() {
        return java.util.Set.copyOf(formats.keySet());
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
