package dev.annachat.api.event;

import dev.annachat.api.context.ChatContext;
import net.kyori.adventure.text.Component;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.Bukkit;

public final class AnnaChatPostEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ChatContext context;
    private final Component renderedMessage;
    private final int scheduledRecipients;

    public AnnaChatPostEvent(ChatContext context, Component renderedMessage, int scheduledRecipients) {
        super(!Bukkit.isPrimaryThread());
        this.context = context;
        this.renderedMessage = renderedMessage;
        this.scheduledRecipients = scheduledRecipients;
    }

    public ChatContext context() { return context; }
    public Component renderedMessage() { return renderedMessage; }
    public int scheduledRecipients() { return scheduledRecipients; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
