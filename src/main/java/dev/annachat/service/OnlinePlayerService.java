package dev.annachat.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 区域线程安全的在线玩家索引，避免从任意实体线程遍历服务端全局玩家集合。
 */
public final class OnlinePlayerService implements Listener {
    private final StateService state;
    private final Map<String, OnlineEntry> byName = new ConcurrentHashMap<>();

    public OnlinePlayerService(StateService state) {
        this.state = state;
    }

    public void initialize() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            index(player);
        }
    }

    public Optional<Player> findExact(String name) {
        return Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT))).map(OnlineEntry::player);
    }

    /**
     * 返回在线索引中预先捕获的不可变身份，不需要从其他区域读取 Player 属性。
     */
    public Optional<PlayerIdentity> identity(String name) {
        return Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT))).map(OnlineEntry::identity);
    }

    public Collection<String> names() {
        return byName.values().stream().map(entry -> entry.identity.name()).toList();
    }

    public int count() {
        return byName.size();
    }

    public record PlayerIdentity(UUID uniqueId, String name) {
    }

    private record OnlineEntry(Player player, PlayerIdentity identity) {
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        index(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        byName.remove(event.getPlayer().getName().toLowerCase(Locale.ROOT));
    }

    private void index(Player player) {
        PlayerIdentity identity = new PlayerIdentity(player.getUniqueId(), player.getName());
        byName.put(identity.name().toLowerCase(Locale.ROOT), new OnlineEntry(player, identity));
        state.rememberIdentity(identity.uniqueId(), identity.name());
    }
}
