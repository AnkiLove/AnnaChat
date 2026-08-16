package dev.annachat.listener;

import dev.annachat.AnnaChat;
import dev.annachat.service.ChatIngressService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public final class ChatListener implements Listener {
    private final AnnaChat plugin;
    private final ChatIngressService ingress;
    private final Set<UUID> cancellationRecoveryLogged = ConcurrentHashMap.newKeySet();

    public ChatListener(AnnaChat plugin, ChatIngressService ingress) {
        this.plugin = plugin;
        this.ingress = ingress;
    }

    public void register(EventPriority priority, boolean respectCancelledEvents, boolean cancelNativeEvent,
                         int fallbackTicks) {
        HandlerList.unregisterAll(this);
        ingress.reset();

        // 旧事件只留下兼容入口；正常情况下会被随后到达的 Paper 事件认领。
        Bukkit.getPluginManager().registerEvent(
                AsyncPlayerChatEvent.class,
                this,
                priority,
                (listener, rawEvent) -> {
                    AsyncPlayerChatEvent event = (AsyncPlayerChatEvent) rawEvent;
                    if (event.isCancelled() && respectCancelledEvents) return;
                    ingress.captureLegacy(event.getPlayer(), event.getMessage(), fallbackTicks);
                },
                plugin,
                respectCancelledEvents
        );

        Bukkit.getPluginManager().registerEvent(
                AsyncChatEvent.class,
                this,
                priority,
                (listener, rawEvent) -> {
                    AsyncChatEvent event = (AsyncChatEvent) rawEvent;
                    boolean recoveredCancellation = event.isCancelled();
                    if (recoveredCancellation && respectCancelledEvents) return;
                    if (cancelNativeEvent) {
                        event.setCancelled(true);
                    } else if (!recoveredCancellation) {
                        // 保持事件链可观察，同时阻止 Paper 再次向原生接收者广播。
                        event.viewers().clear();
                    }
                    String message = PlainTextComponentSerializer.plainText().serialize(event.message());
                    if (recoveredCancellation && cancellationRecoveryLogged.add(event.getPlayer().getUniqueId())) {
                        plugin.getLogger().info("已接管玩家 " + event.getPlayer().getName()
                                + " 被其他插件取消的聊天事件；可通过 settings.respect-cancelled-chat-events 调整此行为");
                    }
                    if (!ingress.acceptPaper(event.getPlayer(), message)) {
                        plugin.getLogger().warning("玩家 " + event.getPlayer().getName()
                                + " 的聊天处理任务无法投递：玩家实体调度器已经停止");
                    }
                },
                plugin,
                respectCancelledEvents
        );

        Bukkit.getPluginManager().registerEvent(
                PlayerQuitEvent.class,
                this,
                EventPriority.MONITOR,
                (listener, rawEvent) -> {
                    UUID playerId = ((PlayerQuitEvent) rawEvent).getPlayer().getUniqueId();
                    ingress.clear(playerId);
                    cancellationRecoveryLogged.remove(playerId);
                },
                plugin,
                false
        );
    }
}
