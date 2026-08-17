package dev.annachat.platform;

import org.bukkit.Bukkit;

/**
 * AnnaChat 的运行平台模式。Paper 与 Folia 使用不同的调度边界，不能只依赖
 * 一个共享的“主线程”概念。
 */
public enum PlatformMode {
    PAPER("Paper", "Bukkit 主线程 + Bukkit 异步调度器"),
    FOLIA("Folia", "玩家实体调度器 + 全局区域调度器");

    private final String displayName;
    private final String schedulerDescription;

    PlatformMode(String displayName, String schedulerDescription) {
        this.displayName = displayName;
        this.schedulerDescription = schedulerDescription;
    }

    public String displayName() {
        return displayName;
    }

    public String schedulerDescription() {
        return schedulerDescription;
    }

    public boolean isFolia() {
        return this == FOLIA;
    }

    /**
     * 通过 Folia 独有的区域服务器类识别平台；服务端名称仅作为日志信息，不参与
     * 决策，避免自定义服务端品牌导致误判。
     */
    public static PlatformMode detect() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false,
                    PlatformMode.class.getClassLoader());
            return FOLIA;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return PAPER;
        }
    }

    public String serverName() {
        return Bukkit.getName();
    }

    public String serverVersion() {
        return Bukkit.getVersion();
    }
}
