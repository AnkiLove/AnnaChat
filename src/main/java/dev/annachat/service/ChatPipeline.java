package dev.annachat.service;

import dev.annachat.AnnaChat;
import dev.annachat.api.ChatChannel;
import dev.annachat.api.FilterResult;
import dev.annachat.api.ModerationMatch;
import dev.annachat.api.PostChatHandler;
import dev.annachat.api.context.ChatContext;
import dev.annachat.api.context.PlayerSnapshot;
import dev.annachat.api.event.AnnaChatPostEvent;
import dev.annachat.api.event.AnnaChatProcessEvent;
import dev.annachat.model.HistoryEntry;
import dev.annachat.model.MuteEntry;
import dev.annachat.util.DurationParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;

public final class ChatPipeline {
    private final AnnaChat plugin;
    private final SchedulerService scheduler;
    private final ChannelService channels;
    private final StateService state;
    private final ProcessorService processors;
    private final FilterService filters;
    private final ContentModerationService moderation;
    private final FormatService formats;
    private final RecipientService recipients;
    private final HistoryService history;
    private final MessageService messages;
    private final List<PostChatHandler> postHandlers = new CopyOnWriteArrayList<>();

    public ChatPipeline(AnnaChat plugin, SchedulerService scheduler, ChannelService channels,
                        StateService state, ProcessorService processors, FilterService filters,
                        ContentModerationService moderation, FormatService formats,
                        RecipientService recipients, HistoryService history,
                        MessageService messages) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.channels = channels;
        this.state = state;
        this.processors = processors;
        this.filters = filters;
        this.moderation = moderation;
        this.formats = formats;
        this.recipients = recipients;
        this.history = history;
        this.messages = messages;
    }

    public void accept(Player sender, String requestedChannel, String rawMessage) {
        scheduler.onEntity(sender, () -> processOnSenderThread(sender, requestedChannel, rawMessage));
    }

    public void registerPostHandler(PostChatHandler handler) {
        postHandlers.add(handler);
    }

    public void unregisterPostHandler(PostChatHandler handler) {
        postHandlers.remove(handler);
    }

    private void processOnSenderThread(Player sender, String requestedChannel, String rawMessage) {
        Lock configLock = plugin.configReadLock();
        configLock.lock();
        try {
        if (!sender.isConnected() || !sender.hasPermission("annachat.use")) return;
        ChannelSelection selection = selectChannel(sender, requestedChannel, rawMessage);
        if (selection == null) return;
        ChatChannel channel = selection.channel;
        String message = selection.message.strip();
        if (message.isEmpty()) return;
        if (!channel.enabled()) {
            messages.send(sender, "channel-not-found", Map.of("channel", channel.id()));
            return;
        }
        if (channel.permission() != null && !channel.permission().isBlank() && !sender.hasPermission(channel.permission())) {
            messages.send(sender, "channel-denied", Map.of("channel", channel.id()));
            return;
        }
        if (message.codePointCount(0, message.length()) > plugin.runtime().maxMessageLength()) {
            messages.send(sender, "too-long", Map.of("max", Integer.toString(plugin.runtime().maxMessageLength())));
            return;
        }
        Optional<MuteEntry> mute = state.mute(sender.getUniqueId());
        if (mute.isPresent()) {
            MuteEntry entry = mute.get();
            messages.send(sender, "muted", Map.of(
                    "remaining", entry.permanent() ? "永久" : DurationParser.format(entry.expiresAt() - System.currentTimeMillis()),
                    "reason", entry.reason()
            ));
            return;
        }
        long cooldown = channel.cooldownMillis() >= 0
                ? channel.cooldownMillis()
                : plugin.runtime().defaultCooldownMillis();
        long remaining = state.cooldownRemaining(sender.getUniqueId(), cooldown);
        if (remaining > 0 && !sender.hasPermission("annachat.bypass.cooldown")) {
            messages.send(sender, "cooldown", Map.of("remaining", String.format(Locale.ROOT, "%.2f", remaining / 1000.0)));
            return;
        }

        ChatContext context = new ChatContext(sender, PlayerSnapshot.capture(sender), channel, message);
        processors.process(context);
        if (context.cancelled()) return;
        if (moderation.enabled() && !sender.hasPermission(moderation.bypassPermission())) {
            // 始终检查玩家提交的原文，防止外部消息处理器先替换文本后绕过审核。
            Optional<ModerationMatch> moderationMatch = moderation.inspect(context.originalMessage());
            if (moderationMatch.isPresent()) {
                ModerationMatch match = moderationMatch.get();
                int warningCount = moderation.recordWarning(sender.getUniqueId());
                // 在返回前提交不可变审计快照；数据库保存原始明文，不保存星号替代文本。
                plugin.database().logModeration(context, match, warningCount);
                messages.send(sender, "moderation-blocked", Map.of(
                        "category", match.categoryDisplayName(),
                        "reason", match.reason(),
                        "count", Integer.toString(warningCount)
                ));
                if (moderation.consoleNotify()) {
                    plugin.getLogger().warning("已拦截玩家 " + context.senderSnapshot().name()
                            + " 的聊天消息；频道=" + channel.id()
                            + "，分类=" + match.categoryId()
                            + "，警告次数=" + warningCount
                            + "；完整原文仅写入已启用的 MySQL 审计表");
                }
                return;
            }
        }
        if (!sender.hasPermission("annachat.bypass.filter")) {
            FilterResult result = filters.process(context);
            if (result.action() == FilterResult.Action.BLOCK) {
                messages.send(sender, "blocked", Map.of("reason", Objects.toString(result.reason(), "未通过规则")));
                return;
            }
            if (result.action() == FilterResult.Action.SHADOW) {
                context.shadow(true);
            }
        }
        AnnaChatProcessEvent processEvent = new AnnaChatProcessEvent(context);
        Bukkit.getPluginManager().callEvent(processEvent);
        if (processEvent.isCancelled()) return;

        Component rendered;
        try {
            rendered = formats.render(context);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("渲染聊天格式失败: " + exception.getMessage());
            messages.send(sender, "blocked", Map.of("reason", "格式渲染失败"));
            return;
        }
        state.markChat(sender.getUniqueId());
        history.add(new HistoryEntry(
                context.createdAt(), sender.getUniqueId(), sender.getName(), channel.id(), context.message()
        ));
        plugin.database().logChat(context);
        if (plugin.runtime().logChat()) {
            plugin.getLogger().info("[" + channel.id() + "] " + sender.getName() + ": " + context.message());
        }
        recipients.dispatch(context, rendered, delivered -> finish(context, rendered, delivered));
        } finally {
            configLock.unlock();
        }
    }

    private void finish(ChatContext context, Component rendered, int delivered) {
        if (delivered <= 1 && plugin.runtime().notifyWhenNoOtherRecipients()) {
            messages.send(context.sender(), "no-recipients");
        }
        Bukkit.getPluginManager().callEvent(new AnnaChatPostEvent(context, rendered, delivered));
        postHandlers.stream()
                .sorted(Comparator.comparingInt(PostChatHandler::priority))
                .forEach(handler -> handler.afterDispatch(context, rendered, delivered));
    }

    private ChannelSelection selectChannel(Player sender, String requested, String rawMessage) {
        String channelId = requested;
        String message = rawMessage;
        if (channelId == null || channelId.isBlank()) {
            String input = message;
            Map.Entry<String, String> matched = plugin.runtime().quickSwitch().entrySet().stream()
                    .filter(entry -> input.startsWith(entry.getKey()))
                    .max(Comparator.comparingInt(entry -> entry.getKey().length()))
                    .orElse(null);
            if (matched != null) {
                channelId = matched.getValue();
                message = message.substring(matched.getKey().length());
            } else {
                channelId = state.channel(sender.getUniqueId(), plugin.runtime().defaultChannel());
            }
        }
        Optional<ChatChannel> channel = channels.find(channelId);
        if (channel.isEmpty()) {
            if (plugin.runtime().cancelWhenNoChannel()) {
                messages.send(sender, "channel-not-found", Map.of("channel", channelId));
            }
            return null;
        }
        return new ChannelSelection(channel.get(), message);
    }

    private record ChannelSelection(ChatChannel channel, String message) {
    }
}
