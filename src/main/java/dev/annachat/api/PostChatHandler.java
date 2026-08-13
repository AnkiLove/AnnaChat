package dev.annachat.api;

import dev.annachat.api.context.ChatContext;
import net.kyori.adventure.text.Component;

@FunctionalInterface
public interface PostChatHandler {
    void afterDispatch(ChatContext context, Component renderedMessage, int scheduledRecipients);

    default int priority() {
        return 1000;
    }
}
