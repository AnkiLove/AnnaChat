package dev.annachat.integration;

import dev.annachat.AnnaChat;
import dev.annachat.model.MuteEntry;
import dev.annachat.util.DurationParser;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;

/**
 * 向 PlaceholderAPI 注册 AnnaChat 自身状态变量。
 *
 * <p>PlaceholderAPI 会在调用方所在的线程执行扩展。AnnaChat 内部只会在发送者
 * 所属的平台调度上下文解析玩家变量；其他插件调用这些变量时，也应遵守当前
 * 服务端平台的线程规则。</p>
 */
public final class AnnaChatExpansion extends PlaceholderExpansion {
    private final AnnaChat plugin;
    private final ThreadLocal<Set<String>> resolving = ThreadLocal.withInitial(ConcurrentHashMap::newKeySet);

    public AnnaChatExpansion(AnnaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "annachat";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AnnaChat";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @NotNull List<String> getPlaceholders() {
        List<String> values = new ArrayList<>(List.of(
                "%annachat_channel%",
                "%annachat_channel_display%",
                "%annachat_muted%",
                "%annachat_mute_remaining%",
                "%annachat_mute_reason%",
                "%annachat_spy%",
                "%annachat_online%",
                "%annachat_version%",
                "%annachat_hidden_<频道ID>%"
        ));
        for (String id : plugin.runtime().customPlaceholders().keySet()) {
            values.add("%annachat_custom_" + id + "%");
        }
        return List.copyOf(values);
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String parameters) {
        Lock lock = plugin.configReadLock();
        lock.lock();
        try {
            return resolve(player, parameters);
        } finally {
            lock.unlock();
        }
    }

    private @Nullable String resolve(Player player, String parameters) {
        String key = parameters.toLowerCase(Locale.ROOT);
        if (key.equals("version")) return plugin.getPluginMeta().getVersion();
        if (key.equals("online")) return Integer.toString(plugin.onlinePlayers().count());

        if (key.startsWith("custom_")) {
            String id = key.substring("custom_".length());
            String template = plugin.text().customPlaceholder(id).orElse(null);
            if (template == null) return null;
            Set<String> stack = resolving.get();
            if (!stack.add(id)) return "";
            try {
                return player == null
                        ? plugin.text().expandWithoutPlayer(template)
                        : plugin.text().expandForPlayer(player, template);
            } finally {
                stack.remove(id);
                if (stack.isEmpty()) resolving.remove();
            }
        }
        if (player == null) return "";

        String channelId = plugin.state().channel(player.getUniqueId(), plugin.runtime().defaultChannel());
        if (key.equals("channel") || key.equals("mode")) return channelId;
        if (key.equals("channel_display")) {
            return plugin.channels().find(channelId).map(value -> value.displayName()).orElse(channelId);
        }
        if (key.equals("spy")) return Boolean.toString(plugin.state().spying(player.getUniqueId()));
        if (key.startsWith("hidden_")) {
            return Boolean.toString(plugin.state().hidden(player.getUniqueId(), key.substring("hidden_".length())));
        }

        Optional<MuteEntry> mute = plugin.state().mute(player.getUniqueId());
        if (key.equals("muted")) return Boolean.toString(mute.isPresent());
        if (key.equals("mute_remaining")) {
            return mute.map(value -> value.permanent()
                    ? "永久"
                    : DurationParser.format(value.expiresAt() - System.currentTimeMillis())).orElse("0秒");
        }
        if (key.equals("mute_reason")) return mute.map(MuteEntry::reason).orElse("");

        return null;
    }
}
