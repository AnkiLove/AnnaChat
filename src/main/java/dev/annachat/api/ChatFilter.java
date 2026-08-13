package dev.annachat.api;

import dev.annachat.api.context.ChatContext;

@FunctionalInterface
public interface ChatFilter {
    FilterResult filter(ChatContext context);

    default int priority() {
        return 1000;
    }
}
