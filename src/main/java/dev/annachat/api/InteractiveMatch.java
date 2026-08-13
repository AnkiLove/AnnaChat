package dev.annachat.api;

import net.kyori.adventure.text.Component;

public record InteractiveMatch(int start, int end, Component component) {
    public InteractiveMatch {
        if (start < 0 || end <= start || component == null) {
            throw new IllegalArgumentException("无效的交互匹配结果");
        }
    }
}
