package dev.annachat.service;

import dev.annachat.api.ChatChannel;
import dev.annachat.config.ConfiguredChannel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ChannelService {
    private volatile Map<String, ConfiguredChannel> configured = Map.of();
    private final Map<String, ChatChannel> external = new ConcurrentHashMap<>();

    public void apply(Map<String, ConfiguredChannel> channels) {
        configured = Map.copyOf(channels);
    }

    public void register(ChatChannel channel) {
        external.put(normalize(channel.id()), channel);
    }

    public void unregister(String id) {
        external.remove(normalize(id));
    }

    public Optional<ChatChannel> find(String id) {
        String key = normalize(id);
        ChatChannel custom = external.get(key);
        return Optional.ofNullable(custom != null ? custom : configured.get(key));
    }

    public Collection<ChatChannel> all() {
        Map<String, ChatChannel> merged = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        merged.putAll(configured);
        merged.putAll(external);
        return List.copyOf(merged.values());
    }

    public java.util.Set<String> ids() {
        java.util.Set<String> ids = new java.util.HashSet<>(configured.keySet());
        ids.addAll(external.keySet());
        return java.util.Set.copyOf(ids);
    }

    private static String normalize(String id) {
        return Objects.requireNonNull(id, "id").toLowerCase(Locale.ROOT);
    }
}
