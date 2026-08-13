package dev.annachat.listener;

import dev.annachat.AnnaChat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.time.Instant;

public final class CommandLogListener implements Listener {
    private final AnnaChat plugin;

    public CommandLogListener(AnnaChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        var player = event.getPlayer();
        plugin.database().logCommand(
                player.getUniqueId().toString(),
                player.getName(),
                player.getWorld().getKey().asString(),
                event.getMessage(),
                Instant.now()
        );
    }
}
