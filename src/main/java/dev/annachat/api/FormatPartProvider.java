package dev.annachat.api;

import dev.annachat.api.context.ChatContext;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;

@FunctionalInterface
public interface FormatPartProvider {
    Component render(ChatContext context, ConfigurationSection configuration);
}
