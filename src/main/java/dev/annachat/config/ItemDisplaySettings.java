package dev.annachat.config;

/**
 * 聊天物品展示的不可变运行配置。
 */
public record ItemDisplaySettings(
        boolean enabled,
        String permission,
        boolean includeArmor,
        boolean includeOffhand
) {
    /**
     * 将可选权限节点统一为非空、无首尾空白的值，避免配置异常导致聊天线程报错。
     */
    public ItemDisplaySettings {
        permission = permission == null ? "" : permission.strip();
    }
}
