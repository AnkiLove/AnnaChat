package dev.annachat.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventPriority;

import java.util.List;
import java.util.Map;

public record RuntimeConfig(
        Map<String, ConfiguredChannel> channels,
        Map<String, FormatDefinition> formats,
        List<ConfiguredInteraction> interactions,
        List<ConfiguredFilter> filters,
        ModerationSettings moderation,
        Map<String, String> quickSwitch,
        String defaultChannel,
        EventPriority eventPriority,
        int historySize,
        int maxMessageLength,
        long defaultCooldownMillis,
        long autosaveSeconds,
        boolean cancelWhenNoChannel,
        boolean notifyWhenNoOtherRecipients,
        boolean logChat,
        String legacyColorPermission,
        String miniMessagePermission,
        String playerMessagePlaceholdersPermission,
        Map<String, String> customPlaceholders,
        Map<String, String> messages,
        YamlConfiguration help,
        YamlConfiguration database
) {
}
