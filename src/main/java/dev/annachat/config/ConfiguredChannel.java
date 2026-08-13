package dev.annachat.config;

import dev.annachat.api.AudienceType;
import dev.annachat.api.ChatChannel;

public record ConfiguredChannel(
        String id,
        String displayName,
        String formatId,
        String permission,
        String receivePermission,
        AudienceType audienceType,
        double radius,
        boolean sameWorld,
        long cooldownMillis,
        int priority,
        boolean enabled
) implements ChatChannel {
}
