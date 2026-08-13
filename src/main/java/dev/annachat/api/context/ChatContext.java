package dev.annachat.api.context;

import dev.annachat.api.ChatChannel;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatContext {
    private final Player sender;
    private final PlayerSnapshot senderSnapshot;
    private final ChatChannel channel;
    private final String originalMessage;
    private final Instant createdAt;
    private final Map<String, Object> metadata = new ConcurrentHashMap<>();
    private volatile String message;
    private volatile boolean cancelled;
    private volatile boolean shadow;

    public ChatContext(Player sender, PlayerSnapshot senderSnapshot, ChatChannel channel, String message) {
        this.sender = Objects.requireNonNull(sender);
        this.senderSnapshot = Objects.requireNonNull(senderSnapshot);
        this.channel = Objects.requireNonNull(channel);
        this.originalMessage = Objects.requireNonNull(message);
        this.message = message;
        this.createdAt = Instant.now();
    }

    public Player sender() { return sender; }
    public PlayerSnapshot senderSnapshot() { return senderSnapshot; }
    public ChatChannel channel() { return channel; }
    public String originalMessage() { return originalMessage; }
    public String message() { return message; }
    public void message(String message) { this.message = Objects.requireNonNull(message); }
    public Instant createdAt() { return createdAt; }
    public Map<String, Object> metadata() { return metadata; }
    public boolean cancelled() { return cancelled; }
    public void cancelled(boolean cancelled) { this.cancelled = cancelled; }
    public boolean shadow() { return shadow; }
    public void shadow(boolean shadow) { this.shadow = shadow; }
}
