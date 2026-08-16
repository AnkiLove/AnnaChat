package dev.annachat.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * 单个玩家的短期聊天入口缓冲区。
 *
 * <p>所有方法都在同一把对象锁内完成，使异步聊天事件与实体回退任务之间的
 * “认领一次”操作具备原子性。</p>
 */
final class ChatIngressBuffer {
    private final Deque<PendingMessage> pending = new ArrayDeque<>();
    private boolean closed;

    synchronized boolean add(PendingMessage message) {
        if (closed) return false;
        pending.addLast(message);
        return true;
    }

    synchronized PendingMessage claimForPaper(String paperMessage) {
        if (closed) return null;
        // 新事件紧跟对应的旧事件到达；从队尾匹配可区分高速重复发送的相同正文。
        Iterator<PendingMessage> iterator = pending.descendingIterator();
        while (iterator.hasNext()) {
            PendingMessage message = iterator.next();
            if (message.matches(paperMessage)) {
                iterator.remove();
                return message;
            }
        }
        // 单条消息允许其他插件改写正文；多条并存时宁可各自处理，也不能错配顺序。
        return pending.size() == 1 ? pending.pollFirst() : null;
    }

    synchronized boolean claim(PendingMessage message) {
        return !closed && pending.removeFirstOccurrence(message);
    }

    synchronized void discard(PendingMessage message) {
        pending.removeFirstOccurrence(message);
    }

    synchronized void close() {
        closed = true;
        pending.clear();
    }

    record PendingMessage(long generation, String senderName, String originalMessage,
                          String channelId, String message, String prefix) {
        boolean matches(String paperMessage) {
            if (paperMessage.equals(originalMessage) || paperMessage.equals(message)) return true;
            return prefix != null
                    && paperMessage.startsWith(prefix)
                    && paperMessage.substring(prefix.length()).equals(message);
        }
    }
}
