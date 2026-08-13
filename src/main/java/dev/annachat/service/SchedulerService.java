package dev.annachat.service;

import dev.annachat.AnnaChat;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.concurrent.TimeUnit;

public final class SchedulerService {
    private final AnnaChat plugin;

    public SchedulerService(AnnaChat plugin) {
        this.plugin = plugin;
    }

    public boolean onEntity(Player player, Runnable task) {
        if (Bukkit.isOwnedByCurrentRegion(player)) {
            task.run();
            return true;
        }
        return player.getScheduler().execute(plugin, task, null, 1L);
    }

    public void global(Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    public void async(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    public ScheduledTask repeatGlobal(Runnable task, long initialTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin, ignored -> task.run(), Math.max(1, initialTicks), Math.max(1, periodTicks)
        );
    }

    public ScheduledTask repeatAsync(Runnable task, long initialSeconds, long periodSeconds) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin, ignored -> task.run(), Math.max(1, initialSeconds), Math.max(1, periodSeconds), TimeUnit.SECONDS
        );
    }

    public void cancelAll() {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }
}
