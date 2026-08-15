package dev.annachat.listener;

import dev.annachat.AnnaChat;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatListener implements Listener {
    private final AnnaChat plugin;
    private final Set<UUID> cancellationRecoveryLogged = ConcurrentHashMap.newKeySet();

    public ChatListener(AnnaChat plugin) {
        this.plugin = plugin;
    }

    public void register(EventPriority priority, boolean respectCancelledEvents) {
        HandlerList.unregisterAll(this);
        Bukkit.getPluginManager().registerEvent(
                AsyncChatEvent.class,
                this,
                priority,
                (listener, rawEvent) -> {
                    AsyncChatEvent event = (AsyncChatEvent) rawEvent;
                    boolean recoveredCancellation = event.isCancelled();
                    if (recoveredCancellation && respectCancelledEvents) return;
                    event.setCancelled(true);
                    String message = PlainTextComponentSerializer.plainText().serialize(event.message());
                    if (recoveredCancellation && cancellationRecoveryLogged.add(event.getPlayer().getUniqueId())) {
                        plugin.getLogger().info("已接管玩家 " + event.getPlayer().getName()
                                + " 被其他插件取消的聊天事件；可通过 settings.respect-cancelled-chat-events 调整此行为");
                    }
                    if (!plugin.pipeline().accept(event.getPlayer(), null, message)) {
                        plugin.getLogger().warning("玩家 " + event.getPlayer().getName()
                                + " 的聊天处理任务无法投递：玩家实体调度器已经停止");
                    }
                },
                plugin,
                respectCancelledEvents
        );
    }
}
