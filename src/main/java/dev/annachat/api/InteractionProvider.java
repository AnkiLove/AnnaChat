package dev.annachat.api;

import dev.annachat.api.context.ChatContext;

import java.util.Optional;

public interface InteractionProvider {
    Optional<InteractiveMatch> find(ChatContext context, String message, int fromIndex);

    default int priority() {
        return 1000;
    }
}
