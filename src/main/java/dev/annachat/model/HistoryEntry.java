package dev.annachat.model;

import java.time.Instant;
import java.util.UUID;

public record HistoryEntry(
        Instant createdAt,
        UUID playerId,
        String playerName,
        String channel,
        String message
) {
}
