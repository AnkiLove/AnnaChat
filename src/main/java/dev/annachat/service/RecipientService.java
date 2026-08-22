package dev.annachat.service;

import dev.annachat.AnnaChat;
import dev.annachat.api.AudienceType;
import dev.annachat.api.RecipientPredicate;
import dev.annachat.api.context.ChatContext;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public final class RecipientService {
    private final AnnaChat plugin;
    private final SchedulerService scheduler;
    private final StateService state;
    private final MentionService mentions;
    private final List<RecipientPredicate> predicates = new CopyOnWriteArrayList<>();

    public RecipientService(AnnaChat plugin, SchedulerService scheduler, StateService state,
                            MentionService mentions) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.state = state;
        this.mentions = mentions;
    }

    public void register(RecipientPredicate predicate) {
        predicates.add(predicate);
    }

    public void unregister(RecipientPredicate predicate) {
        predicates.remove(predicate);
    }

    public void dispatch(ChatContext context, Component message, DispatchComplete completion) {
        scheduler.global(() -> {
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (context.shadow()) {
                online.removeIf(player -> !player.getUniqueId().equals(context.senderSnapshot().uniqueId()));
            }
            if (online.isEmpty()) {
                scheduler.onEntity(context.sender(), () -> completion.complete(0));
                return;
            }
            AtomicInteger remaining = new AtomicInteger(online.size());
            AtomicInteger delivered = new AtomicInteger();
            for (Player candidate : online) {
                Runnable finished = () -> {
                    if (remaining.decrementAndGet() == 0) {
                        scheduler.onEntity(context.sender(), () -> completion.complete(delivered.get()));
                    }
                };
                boolean accepted = candidate.getScheduler().execute(
                        plugin,
                        () -> {
                            try {
                                if (canReceive(context, candidate)) {
                                    Component output = message;
                                    if (isSpyOnly(context, candidate)) {
                                        output = plugin.text().parseRaw(
                                                "<dark_gray>[监听:" + MiniSafe.escape(context.channel().id()) + "]</dark_gray> "
                                        ).append(message);
                                    }
                                    candidate.sendMessage(output);
                                    mentions.notifyIfMentioned(context, candidate);
                                    delivered.incrementAndGet();
                                }
                            } finally {
                                finished.run();
                            }
                        },
                        finished,
                        1L
                );
                if (!accepted) finished.run();
            }
        });
    }

    private boolean canReceive(ChatContext context, Player candidate) {
        boolean sender = candidate.getUniqueId().equals(context.senderSnapshot().uniqueId());
        if (sender) return true;
        if (state.hidden(candidate.getUniqueId(), context.channel().id()) && !state.spying(candidate.getUniqueId())) {
            return false;
        }
        boolean normal = normalAudience(context, candidate);
        if (!normal && !state.spying(candidate.getUniqueId())) return false;
        List<RecipientPredicate> ordered = predicates.stream()
                .sorted(Comparator.comparingInt(RecipientPredicate::priority))
                .toList();
        for (RecipientPredicate predicate : ordered) {
            if (!predicate.canReceive(context, candidate)) return false;
        }
        return true;
    }

    private boolean normalAudience(ChatContext context, Player candidate) {
        return switch (context.channel().audienceType()) {
            case GLOBAL -> true;
            case WORLD -> context.senderSnapshot().worldKey().equals(candidate.getWorld().getKey());
            case LOCAL -> {
                if (!context.senderSnapshot().worldKey().equals(candidate.getWorld().getKey())) yield false;
                double radius = context.channel().radius();
                yield context.senderSnapshot().distanceSquared(candidate.getLocation()) <= radius * radius;
            }
            case FRIENDS -> state.friends(
                    context.senderSnapshot().uniqueId(), candidate.getUniqueId());
            case PERMISSION -> {
                String permission = context.channel().receivePermission();
                if (permission == null || permission.isBlank()) yield true;
                // 支持多个接收权限：使用逗号或 || 分隔，任意一个权限匹配即可。
                yield java.util.Arrays.stream(permission.split("(?:,|\\|\\|)"))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .anyMatch(candidate::hasPermission);
            }
        };
    }

    private boolean isSpyOnly(ChatContext context, Player candidate) {
        if (!state.spying(candidate.getUniqueId())) return false;
        return !normalAudience(context, candidate);
    }

    @FunctionalInterface
    public interface DispatchComplete {
        void complete(int delivered);
    }

    private static final class MiniSafe {
        private static String escape(String value) {
            return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(value);
        }
    }
}
