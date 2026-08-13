package dev.annachat.api;

import dev.annachat.api.context.ChatContext;
import org.bukkit.entity.Player;

/**
 * 在 candidate 自己的实体线程执行，不应阻塞或访问其他区域中的实体。
 */
@FunctionalInterface
public interface RecipientPredicate {
    boolean canReceive(ChatContext context, Player candidate);

    default int priority() {
        return 1000;
    }
}
