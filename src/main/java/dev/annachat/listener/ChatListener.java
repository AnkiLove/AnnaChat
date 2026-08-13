package dev.annachat.listener;

import dev.annachat.AnnaChat;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

public final class ChatListener implements Listener {
    private final AnnaChat plugin;

    public ChatListener(AnnaChat plugin) {
        this.plugin = plugin;
    }

    public void register(EventPriority priority) {
        HandlerList.unregisterAll(this);
        Bukkit.getPluginManager().registerEvent(
                AsyncChatEvent.class,
                this,
                priority,
                (listener, rawEvent) -> {
                    AsyncChatEvent event = (AsyncChatEvent) rawEvent;
                    if (event.isCancelled()) return;
                    event.setCancelled(true);
                    String message = PlainTextComponentSerializer.plainText().serialize(event.message());
                    plugin.pipeline().accept(event.getPlayer(), null, message);
                },
                plugin,
                true
        );
    }
}
