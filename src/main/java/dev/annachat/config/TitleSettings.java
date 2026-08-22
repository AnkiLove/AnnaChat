package dev.annachat.config;

import java.util.List;

/** 称号配置的不可变运行时快照。 */
public record TitleSettings(boolean enabled, String defaultValue, List<TitleRule> rules) {
    public TitleSettings {
        defaultValue = defaultValue == null ? "" : defaultValue;
        rules = List.copyOf(rules == null ? List.of() : rules);
    }

    public record TitleRule(int priority, String permission, String group, String value) {
        public TitleRule {
            permission = permission == null ? "" : permission.strip();
            group = group == null ? "" : group.strip();
            value = value == null ? "" : value;
        }
    }
}
