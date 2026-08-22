package dev.annachat.service;

import dev.annachat.config.RuntimeConfig;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.momirealms.sparrow.message.MiniMessage;
import org.bukkit.entity.Player;

import java.util.Map;

public final class MessageService {
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final TextService textService;
    private volatile Map<String, String> messages = Map.of();

    public MessageService(TextService textService) {
        this.textService = textService;
    }

    public void apply(RuntimeConfig runtime) {
        messages = runtime.messages();
    }

    public Component component(Audience audience, String key, Map<String, String> placeholders) {
        String prefix = messages.getOrDefault("prefix", "");
        String content = messages.getOrDefault(key, "<red>缺少消息: " + key + "</red>");
        String output = prefix + content;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            output = output.replace("{" + entry.getKey() + "}", miniMessage.escapeTags(entry.getValue()));
        }
        if (audience instanceof Player player) {
            output = textService.expandForPlayer(player, output);
        }
        return miniMessage.deserialize(textService.configuredMiniMessageSafe(output));
    }

    public Component component(Audience audience, String key) {
        return component(audience, key, Map.of());
    }

    public void send(Audience audience, String key, Map<String, String> placeholders) {
        audience.sendMessage(component(audience, key, placeholders));
    }

    public void send(Audience audience, String key) {
        audience.sendMessage(component(audience, key));
    }
}
