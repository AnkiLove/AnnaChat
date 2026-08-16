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

    /**
     * 始终延迟到玩家所属实体调度器执行，适用于需要等待当前事件链结束的任务。
     */
    public boolean onEntityLater(Player player, Runnable task, Runnable retired, long delayTicks) {
        return player.getScheduler().execute(plugin, task, retired, Math.max(1L, delayTicks));
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
