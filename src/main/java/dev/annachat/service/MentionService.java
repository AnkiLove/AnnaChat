package dev.annachat.service;

import dev.annachat.api.InteractionProvider;
import dev.annachat.api.InteractiveMatch;
import dev.annachat.api.context.ChatContext;
import dev.annachat.config.MentionSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 解析在线玩家提及、生成补全候选，并在接收者自己的实体线程播放提示音。
 */
public final class MentionService implements InteractionProvider {
    private static final String METADATA_KEY = "annachat:mentioned-players";

    private final OnlinePlayerService onlinePlayers;
    private volatile MentionSettings settings = new MentionSettings(
            true, "annachat.chat.mention", true, true,
            org.bukkit.Sound.BLOCK_ANVIL_USE, org.bukkit.SoundCategory.PLAYERS, 0.8F, 1.2F
    );

    public MentionService(OnlinePlayerService onlinePlayers) {
        this.onlinePlayers = onlinePlayers;
    }

    public void apply(MentionSettings settings) {
        this.settings = settings;
    }

    @Override
    public Optional<InteractiveMatch> find(ChatContext context, String message, int fromIndex) {
        MentionSettings current = settings;
        if (!canMention(context.sender(), current)) return Optional.empty();

        MentionScanner.MentionRange range = MentionScanner.next(message, fromIndex);
        while (range != null) {
            OnlinePlayerService.PlayerIdentity identity = onlinePlayers.identity(range.name()).orElse(null);
            if (identity != null) {
                Component component = Component.text("@" + identity.name(), NamedTextColor.YELLOW)
                    .hoverEvent(Component.text("点击填入私聊命令", NamedTextColor.GRAY))
                    .clickEvent(ClickEvent.suggestCommand("/msg " + identity.name() + " "))
                    .insertion(identity.name());
                return Optional.of(new InteractiveMatch(range.start(), range.end(), component));
            }
            range = MentionScanner.next(message, range.end());
        }
        return Optional.empty();
    }

    @Override
    public int priority() {
        return 50;
    }

    /**
     * 在发送者线程捕获本条消息实际提及的 UUID，后续不再跨区域读取发送者实体。
     */
    public int capture(ChatContext context) {
        MentionSettings current = settings;
        if (!canMention(context.sender(), current)) {
            context.metadata().remove(METADATA_KEY);
            return 0;
        }
        Set<UUID> mentioned = new LinkedHashSet<>();
        MentionScanner.MentionRange range = MentionScanner.next(context.message(), 0);
        while (range != null) {
            onlinePlayers.identity(range.name()).ifPresent(identity -> {
                if (!identity.uniqueId().equals(context.senderSnapshot().uniqueId())) {
                    mentioned.add(identity.uniqueId());
                }
            });
            range = MentionScanner.next(context.message(), range.end());
        }
        if (mentioned.isEmpty()) {
            context.metadata().remove(METADATA_KEY);
        } else {
            context.metadata().put(METADATA_KEY, Set.copyOf(mentioned));
        }
        return mentioned.size();
    }

    /**
     * 调用点必须位于 candidate 自己的实体调度器中。
     */
    public void notifyIfMentioned(ChatContext context, Player candidate) {
        MentionSettings current = settings;
        if (!current.enabled() || !current.soundEnabled() || !isMentioned(context, candidate.getUniqueId())) return;
        candidate.playSound(
                candidate.getLocation(), current.sound(), current.soundCategory(), current.volume(), current.pitch()
        );
    }

    public List<String> complete(String token, UUID senderId) {
        MentionSettings current = settings;
        if (!current.enabled() || !current.autocomplete() || token == null || !token.startsWith("@")) {
            return List.of();
        }
        String prefix = token.substring(1).toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String name : onlinePlayers.names()) {
            OnlinePlayerService.PlayerIdentity identity = onlinePlayers.identity(name).orElse(null);
            if (identity == null || identity.uniqueId().equals(senderId)) continue;
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) matches.add("@" + name);
        }
        matches.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(matches);
    }

    /**
     * 解决旧配置中 @ 同时作为附近频道快捷前缀的冲突。
     */
    public boolean startsWithOnlineMention(Player sender, String input) {
        if (!canMention(sender, settings) || input == null || !input.startsWith("@")) return false;
        MentionScanner.MentionRange range = MentionScanner.next(input, 0);
        return range != null && range.start() == 0 && onlinePlayers.identity(range.name()).isPresent();
    }

    private boolean canMention(Player sender, MentionSettings current) {
        return current.enabled()
                && (current.permission().isBlank() || sender.hasPermission(current.permission()));
    }

    private static boolean isMentioned(ChatContext context, UUID candidateId) {
        Object value = context.metadata().get(METADATA_KEY);
        return value instanceof Set<?> set && set.contains(candidateId);
    }

}
