package dev.annachat.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public record FormatDefinition(String id, String partSeparator, List<Part> parts) {
    public FormatDefinition {
        partSeparator = partSeparator == null ? "" : partSeparator;
        parts = List.copyOf(parts);
    }

    /** 保留旧 API 构造方式，第三方扩展无需修改即可继续注册格式。 */
    public FormatDefinition(String id, List<Part> parts) {
        this(id, "", parts);
    }

    public record Part(String type, ConfigurationSection configuration) {
    }
}
