package dev.annachat.service;

import dev.annachat.AnnaChat;
import dev.annachat.model.MuteEntry;
import dev.annachat.model.FriendResult;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class StateService {
    private final AnnaChat plugin;
    private final SchedulerService scheduler;
    private final File file;
    private final Map<UUID, String> defaultChannels = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> hiddenChannels = new ConcurrentHashMap<>();
    private final Map<UUID, MuteEntry> mutes = new ConcurrentHashMap<>();
    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastChat = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> incomingFriendRequests = new ConcurrentHashMap<>();
    private final Map<UUID, String> knownNames = new ConcurrentHashMap<>();
    private final Map<String, UUID> knownUuidsByName = new ConcurrentHashMap<>();
    private final AtomicReference<Snapshot> pendingSave = new AtomicReference<>();
    private final AtomicBoolean saveWorkerScheduled = new AtomicBoolean();
    private final AtomicLong snapshotRevision = new AtomicLong();
    private long savedRevision;

    public StateService(AnnaChat plugin, SchedulerService scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!file.isFile()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) return;
        for (String rawUuid : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                String path = "players." + rawUuid;
                rememberIdentity(uuid, data.getString(path + ".last-known-name", ""));
                String channel = data.getString(path + ".channel");
                if (channel != null && !channel.isBlank()) defaultChannels.put(uuid, channel);
                hiddenChannels.put(uuid, ConcurrentHashMap.newKeySet());
                hiddenChannels.get(uuid).addAll(data.getStringList(path + ".hidden-channels"));
                if (data.getBoolean(path + ".spy", false)) spies.add(uuid);
                if (data.contains(path + ".mute")) {
                    mutes.put(uuid, new MuteEntry(
                            data.getLong(path + ".mute.expires-at", 0),
                            data.getString(path + ".mute.reason", "未填写")
                    ));
                }
                friends.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet())
                        .addAll(readUuidList(data.getStringList(path + ".friends"), "好友", uuid));
                incomingFriendRequests.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet())
                        .addAll(readUuidList(data.getStringList(path + ".friend-requests"), "好友申请", uuid));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("忽略 data.yml 中无效的 UUID: " + rawUuid);
            }
        }
    }

    public String channel(UUID uuid, String fallback) {
        return defaultChannels.getOrDefault(uuid, fallback);
    }

    public void setChannel(UUID uuid, String channel) {
        defaultChannels.put(uuid, channel);
    }

    /**
     * 清理已经删除的频道状态，并把玩家的旧默认频道迁移到当前默认频道。
     */
    public void reconcileChannels(Set<String> validChannels, String defaultChannel) {
        defaultChannels.replaceAll((uuid, channel) -> validChannels.contains(channel) ? channel : defaultChannel);
        hiddenChannels.values().forEach(values -> values.removeIf(channel -> !validChannels.contains(channel)));
    }

    public void rememberIdentity(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) return;
        String previous = knownNames.put(uuid, name);
        if (previous != null && !previous.equalsIgnoreCase(name)) {
            knownUuidsByName.remove(previous.toLowerCase(Locale.ROOT), uuid);
        }
        knownUuidsByName.put(name.toLowerCase(Locale.ROOT), uuid);
    }

    public Optional<UUID> knownUuid(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(knownUuidsByName.get(name.toLowerCase(Locale.ROOT)));
    }

    public String knownName(UUID uuid) {
        return knownNames.getOrDefault(uuid, uuid.toString());
    }

    public boolean friends(UUID first, UUID second) {
        return friends.getOrDefault(first, Set.of()).contains(second)
                && friends.getOrDefault(second, Set.of()).contains(first);
    }

    /**
     * 创建好友申请；若对方已经向发送者申请，则直接建立双向好友关系。
     */
    public synchronized FriendResult requestFriend(UUID sender, UUID target) {
        if (sender.equals(target)) return FriendResult.SELF;
        if (friends(sender, target)) return FriendResult.ALREADY_FRIENDS;
        Set<UUID> senderRequests = incomingFriendRequests.get(sender);
        if (senderRequests != null && senderRequests.remove(target)) {
            addFriendPair(sender, target);
            return FriendResult.ACCEPTED;
        }
        Set<UUID> requests = incomingFriendRequests.computeIfAbsent(target,
                ignored -> ConcurrentHashMap.newKeySet());
        return requests.add(sender) ? FriendResult.REQUESTED : FriendResult.ALREADY_REQUESTED;
    }

    public synchronized FriendResult acceptFriend(UUID receiver, UUID requester) {
        Set<UUID> requests = incomingFriendRequests.get(receiver);
        if (requests == null || !requests.remove(requester)) return FriendResult.NO_REQUEST;
        addFriendPair(receiver, requester);
        return FriendResult.ACCEPTED;
    }

    public synchronized FriendResult denyFriend(UUID receiver, UUID requester) {
        Set<UUID> requests = incomingFriendRequests.get(receiver);
        return requests != null && requests.remove(requester) ? FriendResult.REMOVED : FriendResult.NO_REQUEST;
    }

    public synchronized FriendResult removeFriend(UUID first, UUID second) {
        Set<UUID> firstFriends = friends.get(first);
        Set<UUID> secondFriends = friends.get(second);
        boolean removedFirst = firstFriends != null && firstFriends.remove(second);
        boolean removedSecond = secondFriends != null && secondFriends.remove(first);
        return removedFirst || removedSecond ? FriendResult.REMOVED : FriendResult.NOT_FRIEND;
    }

    public Set<UUID> friendsOf(UUID uuid) {
        return Set.copyOf(friends.getOrDefault(uuid, Set.of()));
    }

    public Set<UUID> incomingFriendRequests(UUID uuid) {
        return Set.copyOf(incomingFriendRequests.getOrDefault(uuid, Set.of()));
    }

    private void addFriendPair(UUID first, UUID second) {
        friends.computeIfAbsent(first, ignored -> ConcurrentHashMap.newKeySet()).add(second);
        friends.computeIfAbsent(second, ignored -> ConcurrentHashMap.newKeySet()).add(first);
        Set<UUID> firstRequests = incomingFriendRequests.get(first);
        Set<UUID> secondRequests = incomingFriendRequests.get(second);
        if (firstRequests != null) firstRequests.remove(second);
        if (secondRequests != null) secondRequests.remove(first);
    }

    public boolean toggleHidden(UUID uuid, String channel) {
        Set<String> hidden = hiddenChannels.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet());
        if (hidden.remove(channel)) return false;
        hidden.add(channel);
        return true;
    }

    public boolean hidden(UUID uuid, String channel) {
        return hiddenChannels.getOrDefault(uuid, Set.of()).contains(channel);
    }

    public boolean toggleSpy(UUID uuid) {
        if (spies.remove(uuid)) return false;
        spies.add(uuid);
        return true;
    }

    public boolean spying(UUID uuid) {
        return spies.contains(uuid);
    }

    public void mute(UUID uuid, MuteEntry entry) {
        mutes.put(uuid, entry);
    }

    public boolean unmute(UUID uuid) {
        return mutes.remove(uuid) != null;
    }

    public Optional<MuteEntry> mute(UUID uuid) {
        MuteEntry entry = mutes.get(uuid);
        if (entry != null && entry.expired(System.currentTimeMillis())) {
            mutes.remove(uuid, entry);
            return Optional.empty();
        }
        return Optional.ofNullable(entry);
    }

    public long cooldownRemaining(UUID uuid, long cooldownMillis) {
        if (cooldownMillis <= 0) return 0;
        long now = System.currentTimeMillis();
        long previous = lastChat.getOrDefault(uuid, 0L);
        return Math.max(0, cooldownMillis - (now - previous));
    }

    public void markChat(UUID uuid) {
        lastChat.put(uuid, System.currentTimeMillis());
    }

    public void saveAsync() {
        pendingSave.set(snapshot());
        scheduleSaveWorker();
    }

    public void saveBlocking() {
        pendingSave.set(null);
        save(snapshot());
    }

    /**
     * 把密集状态变更合并给同一个异步写入工人，避免 Folia 多个区域线程同时
     * 保存 data.yml。快照修订号还可阻止较旧任务在较新任务之后覆盖磁盘文件。
     */
    private void scheduleSaveWorker() {
        if (saveWorkerScheduled.compareAndSet(false, true)) {
            scheduler.async(this::drainPendingSaves);
        }
    }

    private void drainPendingSaves() {
        try {
            Snapshot next;
            while ((next = pendingSave.getAndSet(null)) != null) {
                save(next);
            }
        } finally {
            saveWorkerScheduled.set(false);
            if (pendingSave.get() != null) scheduleSaveWorker();
        }
    }

    private Snapshot snapshot() {
        Map<UUID, String> channels = Map.copyOf(defaultChannels);
        Map<UUID, Set<String>> hidden = new HashMap<>();
        hiddenChannels.forEach((uuid, values) -> hidden.put(uuid, Set.copyOf(values)));
        Map<UUID, Set<UUID>> friendSnapshot = new HashMap<>();
        friends.forEach((uuid, values) -> friendSnapshot.put(uuid, Set.copyOf(values)));
        Map<UUID, Set<UUID>> requestSnapshot = new HashMap<>();
        incomingFriendRequests.forEach((uuid, values) -> requestSnapshot.put(uuid, Set.copyOf(values)));
        return new Snapshot(snapshotRevision.incrementAndGet(), channels, Map.copyOf(hidden), Map.copyOf(mutes), Set.copyOf(spies),
                Map.copyOf(friendSnapshot), Map.copyOf(requestSnapshot), Map.copyOf(knownNames));
    }

    private synchronized void save(Snapshot snapshot) {
        if (snapshot.revision < savedRevision) return;
        YamlConfiguration data = new YamlConfiguration();
        Set<UUID> players = new HashSet<>();
        players.addAll(snapshot.channels.keySet());
        players.addAll(snapshot.hidden.keySet());
        players.addAll(snapshot.mutes.keySet());
        players.addAll(snapshot.spies);
        players.addAll(snapshot.friends.keySet());
        players.addAll(snapshot.friendRequests.keySet());
        players.addAll(snapshot.knownNames.keySet());
        for (UUID uuid : players) {
            String path = "players." + uuid;
            data.set(path + ".last-known-name", snapshot.knownNames.get(uuid));
            data.set(path + ".channel", snapshot.channels.get(uuid));
            data.set(path + ".hidden-channels", new ArrayList<>(snapshot.hidden.getOrDefault(uuid, Set.of())));
            data.set(path + ".spy", snapshot.spies.contains(uuid));
            MuteEntry mute = snapshot.mutes.get(uuid);
            if (mute != null) {
                data.set(path + ".mute.expires-at", mute.expiresAt());
                data.set(path + ".mute.reason", mute.reason());
            }
            data.set(path + ".friends", snapshot.friends.getOrDefault(uuid, Set.of()).stream()
                    .map(UUID::toString).sorted().toList());
            data.set(path + ".friend-requests", snapshot.friendRequests.getOrDefault(uuid, Set.of()).stream()
                    .map(UUID::toString).sorted().toList());
        }
        try {
            data.save(file);
            savedRevision = snapshot.revision;
        } catch (IOException exception) {
            plugin.getLogger().severe("保存 data.yml 失败: " + exception.getMessage());
        }
    }

    private Set<UUID> readUuidList(List<String> values, String label, UUID owner) {
        Set<UUID> result = new HashSet<>();
        for (String value : values) {
            try {
                result.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("忽略玩家 " + owner + " 的无效" + label + " UUID: " + value);
            }
        }
        return result;
    }

    private record Snapshot(
            long revision,
            Map<UUID, String> channels,
            Map<UUID, Set<String>> hidden,
            Map<UUID, MuteEntry> mutes,
            Set<UUID> spies,
            Map<UUID, Set<UUID>> friends,
            Map<UUID, Set<UUID>> friendRequests,
            Map<UUID, String> knownNames
    ) {
    }
}
