package dev.annachat.api;

import dev.annachat.api.context.ChatContext;

@FunctionalInterface
public interface MessageProcessor {
    String process(ChatContext context, String message);

    default int priority() {
        return 1000;
    }
}
