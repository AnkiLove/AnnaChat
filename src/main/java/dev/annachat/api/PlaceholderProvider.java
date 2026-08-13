package dev.annachat.api;

import dev.annachat.api.context.ChatContext;

@FunctionalInterface
public interface PlaceholderProvider {
    String resolve(ChatContext context);

    default String id() {
        return getClass().getSimpleName();
    }
}
