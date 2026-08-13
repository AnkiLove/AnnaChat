package dev.annachat.service;

import dev.annachat.api.FormatPartProvider;
import dev.annachat.api.context.ChatContext;
import dev.annachat.config.FormatDefinition;
import net.kyori.adventure.text.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class FormatService {
    private final TextService textService;
    private final InteractionService interactionService;
    private volatile Map<String, FormatDefinition> formats = Map.of();
    private final Map<String, FormatPartProvider> providers = new ConcurrentHashMap<>();

    public FormatService(TextService textService, InteractionService interactionService) {
        this.textService = textService;
        this.interactionService = interactionService;
        register("text", (context, config) -> textService.configuredComponent(
                context,
                config.getString("content", ""),
                config.getStringList("hover"),
                config.getString("click.action", ""),
                config.getString("click.value", ""),
                config.getString("insertion", "")
        ));
        register("message", (context, config) -> interactionService.render(context, config.getString("style", "")));
    }

    public void apply(Map<String, FormatDefinition> formats) {
        for (FormatDefinition format : formats.values()) {
            for (FormatDefinition.Part part : format.parts()) {
                if (!providers.containsKey(part.type())) {
                    throw new IllegalArgumentException("格式 " + format.id() + " 使用了未注册的片段类型 " + part.type());
                }
            }
        }
        this.formats = Map.copyOf(formats);
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
        FormatDefinition format = formats.get(formatId.toLowerCase(Locale.ROOT));
        if (format == null) throw new IllegalStateException("找不到聊天格式 " + context.channel().formatId());
        Component result = Component.empty();
        for (FormatDefinition.Part part : format.parts()) {
            FormatPartProvider provider = providers.get(part.type());
            if (provider == null) throw new IllegalStateException("找不到格式片段提供器 " + part.type());
            result = result.append(provider.render(context, part.configuration()));
        }
        return result;
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
