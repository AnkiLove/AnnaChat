package dev.annachat.service;

import dev.annachat.AnnaChat;
import dev.annachat.platform.PlatformMode;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

public final class SchedulerService {
    private final AnnaChat plugin;
    private final PlatformMode platformMode;

    public SchedulerService(AnnaChat plugin, PlatformMode platformMode) {
        this.plugin = plugin;
        this.platformMode = platformMode;
    }

    public boolean onEntity(Player player, Runnable task) {
        if (platformMode == PlatformMode.PAPER) {
            if (Bukkit.isPrimaryThread()) {
                task.run();
                return true;
            }
            return Bukkit.getScheduler().runTask(plugin, task) != null;
        }
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
        if (platformMode == PlatformMode.PAPER) {
            return Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks)) != null;
        }
        return player.getScheduler().execute(plugin, task, retired, Math.max(1L, delayTicks));
    }

    public void global(Runnable task) {
        if (platformMode == PlatformMode.PAPER) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    public void async(Runnable task) {
        if (platformMode == PlatformMode.PAPER) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return;
        }
        Bukkit.getAsyncScheduler().runNow(plugin, ignored -> task.run());
    }

    public TaskHandle repeatGlobal(Runnable task, long initialTicks, long periodTicks) {
        if (platformMode == PlatformMode.PAPER) {
            BukkitTask scheduled = Bukkit.getScheduler().runTaskTimer(
                    plugin, task, Math.max(1, initialTicks), Math.max(1, periodTicks)
            );
            return new TaskHandle(scheduled::cancel);
        }
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin, ignored -> task.run(), Math.max(1, initialTicks), Math.max(1, periodTicks)
        );
        return new TaskHandle(scheduled::cancel);
    }

    public TaskHandle repeatAsync(Runnable task, long initialSeconds, long periodSeconds) {
        if (platformMode == PlatformMode.PAPER) {
            BukkitTask scheduled = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    plugin, task, Math.max(1, initialSeconds * 20L), Math.max(1, periodSeconds * 20L)
            );
            return new TaskHandle(scheduled::cancel);
        }
        ScheduledTask scheduled = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin, ignored -> task.run(), Math.max(1, initialSeconds), Math.max(1, periodSeconds), TimeUnit.SECONDS
        );
        return new TaskHandle(scheduled::cancel);
    }

    public void cancelAll() {
        if (platformMode == PlatformMode.PAPER) {
            Bukkit.getScheduler().cancelTasks(plugin);
            return;
        }
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }

    public PlatformMode platformMode() {
        return platformMode;
    }

    /** 统一封装 Paper BukkitTask 与 Folia ScheduledTask 的取消操作。 */
    public static final class TaskHandle {
        private final Runnable cancelAction;

        private TaskHandle(Runnable cancelAction) {
            this.cancelAction = cancelAction;
        }

        public void cancel() {
            cancelAction.run();
        }
    }
}
