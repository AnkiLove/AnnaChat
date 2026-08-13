package dev.annachat.config;

import java.util.List;
import java.util.Set;

/**
 * 内容审核的不可变运行配置。配置加载阶段完成校验，运行阶段只读取快照。
 */
public record ModerationSettings(
        boolean enabled,
        String bypassPermission,
        boolean ignoreWhitespace,
        boolean ignorePunctuation,
        Set<Integer> ignoredCodePoints,
        int warningResetSeconds,
        boolean consoleNotify,
        List<Category> categories
) {
    public record Category(
            String id,
            String displayName,
            int priority,
            String reason,
            List<String> words,
            List<String> whitelist
    ) {
    }
}
