package dev.annachat.config;

import dev.annachat.service.LuckPermsGroupService;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 按权限节点或 LuckPerms 主组匹配聊天策略的不可变快照。 */
public record GroupSettings(
        boolean enabled,
        String detectionMode,
        GroupPolicy defaults,
        List<Rule> rules
) {
    public GroupSettings {
        detectionMode = detectionMode == null ? "PERMISSION" : detectionMode.strip().toUpperCase(Locale.ROOT);
        defaults = defaults == null ? GroupPolicy.defaults() : defaults;
        rules = rules == null ? List.of() : rules.stream()
                .sorted(Comparator.comparingInt(Rule::priority).reversed())
                .toList();
    }

    public GroupPolicy resolve(Player player) {
        if (!enabled) return defaults;
        LuckPermsGroupService groups = new LuckPermsGroupService();
        String primaryGroup = (detectionMode.equals("LUCKPERMS_GROUP") || detectionMode.equals("ANY"))
                ? groups.primaryGroup(player).orElse("") : "";
        for (Rule rule : rules) {
            boolean permissionMatch = !rule.permission().isBlank() && player.hasPermission(rule.permission());
            boolean groupMatch = !rule.group().isBlank()
                    && primaryGroup.equalsIgnoreCase(rule.group());
            boolean matched = switch (detectionMode) {
                case "LUCKPERMS_GROUP" -> groupMatch;
                case "ANY" -> permissionMatch || groupMatch;
                default -> permissionMatch;
            };
            if (matched) return rule.policy();
        }
        return defaults;
    }

    public record Rule(int priority, String permission, String group, GroupPolicy policy) {
        public Rule {
            permission = permission == null ? "" : permission.strip();
            group = group == null ? "" : group.strip();
            policy = policy == null ? GroupPolicy.defaults() : policy;
        }
    }
}
