package dev.annachat.config;

/** 按权限或 LuckPerms 主组选择聊天格式的规则。 */
public record FormatRule(String baseFormat, int priority, String permission, String group, String format) {
    public FormatRule {
        baseFormat = baseFormat == null ? "" : baseFormat.strip().toLowerCase(java.util.Locale.ROOT);
        permission = permission == null ? "" : permission.strip();
        group = group == null ? "" : group.strip();
        format = format == null ? "" : format.strip().toLowerCase(java.util.Locale.ROOT);
    }
}
