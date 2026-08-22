package dev.annachat.config;

/** 权限组可覆盖的聊天限制；负数表示沿用频道或主配置。 */
public record GroupPolicy(
        long cooldownMillis,
        int maxMessageLength,
        int maxMentions,
        boolean bypassModeration,
        boolean bypassFilter,
        boolean bypassCooldown
) {
    public static GroupPolicy defaults() {
        return new GroupPolicy(-1L, -1, -1, false, false, false);
    }
}
