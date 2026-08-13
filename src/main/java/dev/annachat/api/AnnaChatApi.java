package dev.annachat.api;

import dev.annachat.api.context.ChatContext;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * AnnaChat 的稳定开发接口。所有聊天处理回调都运行在发送者所属的实体线程；
 * 接收者判断回调运行在对应接收者所属的实体线程。
 */
public interface AnnaChatApi {
    void registerChannel(ChatChannel channel);
    void unregisterChannel(String id);
    Optional<ChatChannel> channel(String id);
    Collection<ChatChannel> channels();

    /**
     * 使用当前热重载词库检查一段纯文本。该方法不访问玩家实体，可从任意线程调用。
     */
    Optional<ModerationMatch> inspectModeration(String message);

    boolean areFriends(UUID first, UUID second);
    Set<UUID> friendsOf(UUID player);

    void registerPlaceholder(PlaceholderProvider provider);
    void unregisterPlaceholder(String id);
    void registerProcessor(MessageProcessor processor);
    void unregisterProcessor(MessageProcessor processor);
    void registerFilter(ChatFilter filter);
    void unregisterFilter(ChatFilter filter);
    void registerInteraction(InteractionProvider provider);
    void unregisterInteraction(InteractionProvider provider);
    void registerFormatPart(String type, FormatPartProvider provider);
    void unregisterFormatPart(String type);
    void registerRecipientPredicate(RecipientPredicate predicate);
    void unregisterRecipientPredicate(RecipientPredicate predicate);
    void registerPostHandler(PostChatHandler handler);
    void unregisterPostHandler(PostChatHandler handler);

    /**
     * 可从任意线程调用。实现会自动切换到发送者所属区域线程。
     */
    void send(Player sender, String channelId, String message);

    /**
     * 当前回调是否处在插件认可的发送者处理上下文中。
     */
    boolean isOwnedBySender(ChatContext context);
}
