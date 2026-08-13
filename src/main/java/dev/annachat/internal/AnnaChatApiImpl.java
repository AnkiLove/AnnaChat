package dev.annachat.internal;

import dev.annachat.AnnaChat;
import dev.annachat.api.*;
import dev.annachat.api.context.ChatContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class AnnaChatApiImpl implements AnnaChatApi {
    private final AnnaChat plugin;

    public AnnaChatApiImpl(AnnaChat plugin) {
        this.plugin = plugin;
    }

    @Override public void registerChannel(ChatChannel channel) { plugin.channels().register(channel); }
    @Override public void unregisterChannel(String id) { plugin.channels().unregister(id); }
    @Override public Optional<ChatChannel> channel(String id) { return plugin.channels().find(id); }
    @Override public Collection<ChatChannel> channels() { return plugin.channels().all(); }
    @Override public Optional<ModerationMatch> inspectModeration(String message) { return plugin.moderation().inspect(message); }
    @Override public boolean areFriends(UUID first, UUID second) { return plugin.state().friends(first, second); }
    @Override public Set<UUID> friendsOf(UUID player) { return plugin.state().friendsOf(player); }
    @Override public void registerPlaceholder(PlaceholderProvider provider) { plugin.text().register(provider); }
    @Override public void unregisterPlaceholder(String id) { plugin.text().unregister(id); }
    @Override public void registerProcessor(MessageProcessor processor) { plugin.processors().register(processor); }
    @Override public void unregisterProcessor(MessageProcessor processor) { plugin.processors().unregister(processor); }
    @Override public void registerFilter(ChatFilter filter) { plugin.filters().register(filter); }
    @Override public void unregisterFilter(ChatFilter filter) { plugin.filters().unregister(filter); }
    @Override public void registerInteraction(InteractionProvider provider) { plugin.interactions().register(provider); }
    @Override public void unregisterInteraction(InteractionProvider provider) { plugin.interactions().unregister(provider); }
    @Override public void registerFormatPart(String type, FormatPartProvider provider) { plugin.formats().register(type, provider); }
    @Override public void unregisterFormatPart(String type) { plugin.formats().unregister(type); }
    @Override public void registerRecipientPredicate(RecipientPredicate predicate) { plugin.recipients().register(predicate); }
    @Override public void unregisterRecipientPredicate(RecipientPredicate predicate) { plugin.recipients().unregister(predicate); }
    @Override public void registerPostHandler(PostChatHandler handler) { plugin.pipeline().registerPostHandler(handler); }
    @Override public void unregisterPostHandler(PostChatHandler handler) { plugin.pipeline().unregisterPostHandler(handler); }
    @Override public void send(Player sender, String channelId, String message) { plugin.pipeline().accept(sender, channelId, message); }
    @Override public boolean isOwnedBySender(ChatContext context) { return Bukkit.isOwnedByCurrentRegion(context.sender()); }
}
