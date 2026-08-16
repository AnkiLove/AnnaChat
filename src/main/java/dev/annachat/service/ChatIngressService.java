package dev.annachat.service;

import dev.annachat.AnnaChat;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static dev.annachat.service.ChatIngressBuffer.PendingMessage;

/**
 * 统一衔接旧式与 Paper 聊天事件，保证每条玩家输入最多进入一次正式管线。
 *
 * <p>Paper 会在兼容旧聊天事件后派发 {@code AsyncChatEvent}。正常情况下由新事件
 * 认领旧事件留下的消息；如果转换链被其他插件中断，实体调度器会在指定 tick 后
 * 处理旧事件副本，避免玩家进服后的第一条消息永久丢失。</p>
 */
public final class ChatIngressService {
    private final AnnaChat plugin;
    private final SchedulerService scheduler;
    private final Map<UUID, ChatIngressBuffer> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> fallbackRecoveryLogged = ConcurrentHashMap.newKeySet();
    private final AtomicLong generation = new AtomicLong();

    public ChatIngressService(AnnaChat plugin, SchedulerService scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
    }

    /**
     * 捕获兼容事件，但不在异步事件线程访问玩家状态或执行聊天业务。
     */
    public void captureLegacy(Player sender, String rawMessage, int fallbackTicks) {
        QuickSwitch switchResult = extractQuickSwitch(rawMessage);
        PendingMessage pending = new PendingMessage(
                generation.get(),
                sender.getName(),
                rawMessage,
                switchResult.channelId(),
                switchResult.message(),
                switchResult.prefix()
        );
        ChatIngressBuffer session = sessions.computeIfAbsent(sender.getUniqueId(), ignored -> new ChatIngressBuffer());
        if (!session.add(pending)) return;

        boolean scheduled = scheduler.onEntityLater(
                sender,
                () -> deliverFallback(sender, session, pending),
                () -> session.discard(pending),
                fallbackTicks
        );
        if (!scheduled) session.discard(pending);
    }

    /**
     * Paper 事件按正文认领同一玩家最新的匹配项，并保留新事件中的最终文本改动。
     */
    public boolean acceptPaper(Player sender, String paperMessage) {
        ChatIngressBuffer session = sessions.computeIfAbsent(sender.getUniqueId(), ignored -> new ChatIngressBuffer());
        PendingMessage pending = session.claimForPaper(paperMessage);
        if (pending == null) {
            return plugin.pipeline().accept(sender, null, paperMessage);
        }

        String message = paperMessage;
        if (pending.prefix() != null && paperMessage.startsWith(pending.prefix())) {
            message = paperMessage.substring(pending.prefix().length());
        }
        return plugin.pipeline().accept(sender, pending.channelId(), message);
    }

    public void clear(UUID playerId) {
        ChatIngressBuffer session = sessions.remove(playerId);
        if (session != null) session.close();
        fallbackRecoveryLogged.remove(playerId);
    }

    /**
     * 热重载时使旧回退任务失效，防止旧入口使用已经切换掉的配置。
     */
    public void reset() {
        generation.incrementAndGet();
        sessions.values().forEach(ChatIngressBuffer::close);
        sessions.clear();
    }

    private void deliverFallback(Player sender, ChatIngressBuffer session, PendingMessage pending) {
        if (pending.generation() != generation.get() || !session.claim(pending)) return;
        if (plugin.pipeline().accept(sender, pending.channelId(), pending.message())) {
            if (fallbackRecoveryLogged.add(sender.getUniqueId())) {
                plugin.getLogger().info("已通过兼容事件回退接管玩家 " + pending.senderName()
                        + " 的聊天消息；Paper 新聊天事件未到达");
            }
        } else {
            plugin.getLogger().warning("玩家 " + pending.senderName() + " 的兼容聊天回退任务无法投递");
        }
    }

    private QuickSwitch extractQuickSwitch(String rawMessage) {
        Map.Entry<String, String> matched = plugin.runtime().quickSwitch().entrySet().stream()
                .filter(entry -> rawMessage.startsWith(entry.getKey()))
                .max(Comparator.comparingInt(entry -> entry.getKey().length()))
                .orElse(null);
        if (matched == null) return new QuickSwitch(null, rawMessage, null);
        return new QuickSwitch(
                matched.getValue(),
                rawMessage.substring(matched.getKey().length()),
                matched.getKey()
        );
    }

    private record QuickSwitch(String channelId, String message, String prefix) {
    }

}
