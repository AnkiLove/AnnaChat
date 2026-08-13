package dev.annachat.api.event;

import dev.annachat.api.context.ChatContext;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.Bukkit;

public final class AnnaChatProcessEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ChatContext context;

    public AnnaChatProcessEvent(ChatContext context) {
        super(!Bukkit.isPrimaryThread());
        this.context = context;
    }

    public ChatContext context() {
        return context;
    }

    @Override
    public boolean isCancelled() {
        return context.cancelled();
    }

    @Override
    public void setCancelled(boolean cancel) {
        context.cancelled(cancel);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
