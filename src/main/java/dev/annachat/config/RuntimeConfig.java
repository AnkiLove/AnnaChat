package dev.annachat.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventPriority;

import java.util.List;
import java.util.Map;

public record RuntimeConfig(
        Map<String, ConfiguredChannel> channels,
        Map<String, FormatDefinition> formats,
        List<FormatRule> formatRules,
        TitleSettings titles,
        GroupSettings groups,
        List<ConfiguredInteraction> interactions,
        List<ConfiguredFilter> filters,
        ModerationSettings moderation,
        ItemDisplaySettings itemDisplay,
        MentionSettings mentions,
        Map<String, String> quickSwitch,
        String defaultChannel,
        EventPriority eventPriority,
        boolean respectCancelledChatEvents,
        boolean cancelNativeChatEvent,
        int legacyEventFallbackTicks,
        int historySize,
        int maxMessageLength,
        long defaultCooldownMillis,
        long autosaveSeconds,
        boolean cancelWhenNoChannel,
        boolean notifyWhenNoOtherRecipients,
        boolean logChat,
        String legacyColorPermission,
        Map<String, String> legacyColorPermissions,
        String hexColorPermission,
        String miniMessagePermission,
        String playerMessagePlaceholdersPermission,
        boolean stripUnresolvedPlaceholders,
        Map<String, String> customPlaceholders,
        Map<String, String> messages,
        YamlConfiguration help,
        YamlConfiguration database
) {
}
