package dev.annachat.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public record FormatDefinition(String id, List<Part> parts) {
    public record Part(String type, ConfigurationSection configuration) {
    }
}
