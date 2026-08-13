package dev.annachat.command;

import dev.annachat.AnnaChat;
import dev.annachat.api.ChatChannel;
import dev.annachat.api.context.ChatContext;
import dev.annachat.api.context.PlayerSnapshot;
import dev.annachat.model.HistoryEntry;
import dev.annachat.model.FriendResult;
import dev.annachat.model.MuteEntry;
import dev.annachat.util.DurationParser;
import dev.annachat.service.OnlinePlayerService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class AnnaChatCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final AnnaChat plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public AnnaChatCommand(AnnaChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender, args.length > 1 ? args[1] : "1");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "channel" -> channel(sender, args);
            case "toggle" -> toggle(sender, args);
            case "friend" -> friend(sender, args);
            case "reload" -> reload(sender);
            case "mute" -> mute(sender, args);
            case "unmute" -> unmute(sender, args);
            case "spy" -> spy(sender);
            case "history" -> history(sender, args);
            case "preview" -> preview(sender, args);
            default -> plugin.messages().send(sender, "usage");
        }
        return true;
    }

    private void help(CommandSender sender, String rawPage) {
        int requested;
        try {
            requested = Integer.parseInt(rawPage);
        } catch (NumberFormatException exception) {
            plugin.messages().send(sender, "invalid-page");
            return;
        }
        var help = plugin.runtime().help();
        var pages = help.getConfigurationSection("help.pages");
        if (pages == null || pages.getKeys(false).isEmpty()) {
            plugin.messages().send(sender, "usage");
            return;
        }
        int total = pages.getKeys(false).stream().mapToInt(Integer::parseInt).max().orElse(1);
        int page = Math.max(1, Math.min(total, requested));
        Map<String, String> values = Map.of(
                "{page}", Integer.toString(page),
                "{pages}", Integer.toString(total),
                "{previous}", Integer.toString(page <= 1 ? total : page - 1),
                "{next}", Integer.toString(page >= total ? 1 : page + 1)
        );
        sendMini(sender, replace(help.getString("help.header", ""), values));
        String category = switch (page) {
            case 1 -> "common";
            case 2 -> "friend";
            case 3 -> "manage";
            default -> "inspect";
        };
        sendMini(sender, help.getString("help.category." + category, ""));
        for (String line : help.getStringList("help.pages." + page)) {
            sendMini(sender, replace(line, values));
        }
        sendMini(sender, replace(help.getString("help.footer", ""), values));
    }

    private void channel(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!player.hasPermission("annachat.command.channel")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (args.length < 2) {
            String current = plugin.state().channel(player.getUniqueId(), plugin.runtime().defaultChannel());
            plugin.messages().send(player, "channel-selected", Map.of("channel", current));
            return;
        }
        plugin.channels().find(args[1]).ifPresentOrElse(channel -> {
            if (!channel.enabled() || (!channel.permission().isBlank() && !player.hasPermission(channel.permission()))) {
                plugin.messages().send(player, "channel-denied", Map.of("channel", channel.id()));
                return;
            }
            plugin.state().setChannel(player.getUniqueId(), channel.id());
            plugin.messages().send(player, "channel-selected", Map.of("channel", channel.displayName()));
        }, () -> plugin.messages().send(player, "channel-not-found", Map.of("channel", args[1])));
    }

    private void toggle(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!player.hasPermission("annachat.command.toggle")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "usage");
            return;
        }
        plugin.channels().find(args[1]).ifPresentOrElse(channel -> {
            boolean hidden = plugin.state().toggleHidden(player.getUniqueId(), channel.id());
            plugin.messages().send(player, hidden ? "channel-hidden" : "channel-visible",
                    Map.of("channel", channel.displayName()));
        }, () -> plugin.messages().send(player, "channel-not-found", Map.of("channel", args[1])));
    }

    private void friend(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!player.hasPermission("annachat.command.friend")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        plugin.state().rememberIdentity(player.getUniqueId(), player.getName());
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            showFriends(player);
            return;
        }
        if (args[1].equalsIgnoreCase("requests")) {
            showFriendRequests(player);
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(player, "friend-usage");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> addFriend(player, args[2]);
            case "accept" -> acceptFriend(player, args[2]);
            case "deny" -> denyFriend(player, args[2]);
            case "remove" -> removeFriend(player, args[2]);
            default -> plugin.messages().send(player, "friend-usage");
        }
    }

    private void addFriend(Player sender, String targetName) {
        OnlinePlayerService.PlayerIdentity target = plugin.onlinePlayers().identity(targetName).orElse(null);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", Map.of("player", targetName));
            return;
        }
        plugin.state().rememberIdentity(target.uniqueId(), target.name());
        FriendResult result = plugin.state().requestFriend(sender.getUniqueId(), target.uniqueId());
        switch (result) {
            case SELF -> plugin.messages().send(sender, "friend-self");
            case ALREADY_FRIENDS -> plugin.messages().send(sender, "friend-already",
                    Map.of("player", target.name()));
            case ALREADY_REQUESTED -> plugin.messages().send(sender, "friend-request-exists",
                    Map.of("player", target.name()));
            case REQUESTED -> {
                plugin.messages().send(sender, "friend-request-sent", Map.of("player", target.name()));
                notifyOnline(target.name(), "friend-request-received", Map.of("player", sender.getName()));
                plugin.state().saveAsync();
            }
            case ACCEPTED -> {
                plugin.messages().send(sender, "friend-accepted", Map.of("player", target.name()));
                notifyOnline(target.name(), "friend-accepted-by", Map.of("player", sender.getName()));
                plugin.state().saveAsync();
            }
            default -> plugin.messages().send(sender, "friend-usage");
        }
    }

    private void acceptFriend(Player receiver, String requesterName) {
        UUID requester = plugin.state().knownUuid(requesterName).orElse(null);
        if (requester == null || plugin.state().acceptFriend(receiver.getUniqueId(), requester) == FriendResult.NO_REQUEST) {
            plugin.messages().send(receiver, "friend-request-missing", Map.of("player", requesterName));
            return;
        }
        String name = plugin.state().knownName(requester);
        plugin.messages().send(receiver, "friend-accepted", Map.of("player", name));
        notifyOnline(name, "friend-accepted-by", Map.of("player", receiver.getName()));
        plugin.state().saveAsync();
    }

    private void denyFriend(Player receiver, String requesterName) {
        UUID requester = plugin.state().knownUuid(requesterName).orElse(null);
        if (requester == null || plugin.state().denyFriend(receiver.getUniqueId(), requester) == FriendResult.NO_REQUEST) {
            plugin.messages().send(receiver, "friend-request-missing", Map.of("player", requesterName));
            return;
        }
        plugin.messages().send(receiver, "friend-denied", Map.of("player", plugin.state().knownName(requester)));
        plugin.state().saveAsync();
    }

    private void removeFriend(Player sender, String friendName) {
        UUID friend = plugin.state().knownUuid(friendName).orElse(null);
        if (friend == null || plugin.state().removeFriend(sender.getUniqueId(), friend) == FriendResult.NOT_FRIEND) {
            plugin.messages().send(sender, "friend-not-found", Map.of("player", friendName));
            return;
        }
        String name = plugin.state().knownName(friend);
        plugin.messages().send(sender, "friend-removed", Map.of("player", name));
        notifyOnline(name, "friend-removed-by", Map.of("player", sender.getName()));
        plugin.state().saveAsync();
    }

    private void showFriends(Player player) {
        List<String> names = plugin.state().friendsOf(player.getUniqueId()).stream()
                .map(plugin.state()::knownName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (names.isEmpty()) {
            plugin.messages().send(player, "friend-list-empty");
            return;
        }
        plugin.messages().send(player, "friend-list-header", Map.of("count", Integer.toString(names.size())));
        for (String name : names) {
            sendMini(player, "<dark_gray>•</dark_gray> <click:suggest_command:'/msg "
                    + miniMessage.escapeTags(name) + " '><aqua>" + miniMessage.escapeTags(name)
                    + "</aqua></click>");
        }
    }

    private void showFriendRequests(Player player) {
        List<String> names = plugin.state().incomingFriendRequests(player.getUniqueId()).stream()
                .map(plugin.state()::knownName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (names.isEmpty()) {
            plugin.messages().send(player, "friend-requests-empty");
            return;
        }
        plugin.messages().send(player, "friend-requests-header", Map.of("count", Integer.toString(names.size())));
        for (String name : names) {
            String safe = miniMessage.escapeTags(name);
            sendMini(player, "<dark_gray>•</dark_gray> <white>" + safe + "</white> "
                    + "<click:run_command:'/annachat friend accept " + safe + "'><aqua>[接受]</aqua></click> "
                    + "<click:run_command:'/annachat friend deny " + safe + "'><red>[拒绝]</red></click>");
        }
    }

    private void notifyOnline(String playerName, String messageKey, Map<String, String> placeholders) {
        plugin.onlinePlayers().findExact(playerName).ifPresent(target ->
                plugin.scheduler().onEntity(target, () -> plugin.messages().send(target, messageKey, placeholders)));
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("annachat.admin.reload")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        plugin.reloadAll(sender);
    }

    private void mute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("annachat.admin.mute")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "usage");
            return;
        }
        OnlinePlayerService.PlayerIdentity target = plugin.onlinePlayers().identity(args[1]).orElse(null);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", Map.of("player", args[1]));
            return;
        }
        long duration;
        try {
            duration = DurationParser.parseMillis(args.length >= 3 ? args[2] : "永久");
        } catch (IllegalArgumentException exception) {
            plugin.messages().send(sender, "invalid-duration");
            return;
        }
        String reason = args.length >= 4 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "管理员操作";
        long expiresAt = duration <= 0 ? 0 : System.currentTimeMillis() + duration;
        plugin.state().mute(target.uniqueId(), new MuteEntry(expiresAt, reason));
        plugin.messages().send(sender, "mute-success", Map.of(
                "player", target.name(),
                "duration", duration <= 0 ? "永久" : DurationParser.format(duration)
        ));
    }

    private void unmute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("annachat.admin.mute")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "usage");
            return;
        }
        OnlinePlayerService.PlayerIdentity target = plugin.onlinePlayers().identity(args[1]).orElse(null);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", Map.of("player", args[1]));
            return;
        }
        boolean removed = plugin.state().unmute(target.uniqueId());
        plugin.messages().send(sender, removed ? "unmute-success" : "not-muted", Map.of("player", target.name()));
    }

    private void spy(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!player.hasPermission("annachat.admin.spy")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        boolean enabled = plugin.state().toggleSpy(player.getUniqueId());
        plugin.messages().send(player, enabled ? "spy-enabled" : "spy-disabled");
    }

    private void history(CommandSender sender, String[] args) {
        if (!sender.hasPermission("annachat.admin.history")) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        int page = 1;
        if (args.length > 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException exception) {
                plugin.messages().send(sender, "invalid-page");
                return;
            }
        }
        List<HistoryEntry> entries = plugin.history().snapshot();
        int pageSize = 8;
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        page = Math.min(page, pages);
        sendMini(sender, "<gray>聊天历史 <dark_gray>-</dark_gray> 第 <aqua>" + page + "</aqua>/<aqua>" + pages + "</aqua> 页</gray>");
        int start = (page - 1) * pageSize;
        for (int i = start; i < Math.min(entries.size(), start + pageSize); i++) {
            HistoryEntry entry = entries.get(i);
            String safeName = miniMessage.escapeTags(entry.playerName());
            String safeChannel = miniMessage.escapeTags(entry.channel());
            String safeMessage = miniMessage.escapeTags(entry.message());
            sendMini(sender, "<dark_gray>•</dark_gray> <gray>" + TIME.format(entry.createdAt()) + "</gray> "
                    + "<aqua>[" + safeChannel + "]</aqua> <white>" + safeName + "</white><dark_gray>:</dark_gray> <gray>"
                    + safeMessage + "</gray>");
        }
        if (entries.isEmpty()) sendMini(sender, "<gray>暂无聊天记录。</gray>");
    }

    private void preview(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) return;
        if (!player.hasPermission("annachat.admin.preview")) {
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (args.length < 2 || !plugin.formats().contains(args[1])) {
            plugin.messages().send(player, "usage");
            return;
        }
        ChatChannel channel = plugin.channels().all().stream()
                .filter(value -> value.formatId().equalsIgnoreCase(args[1]))
                .findFirst()
                .orElseGet(() -> plugin.channels().find(plugin.runtime().defaultChannel()).orElseThrow());
        String message = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "这是一条 AnnaChat 格式预览消息";
        ChatContext context = new ChatContext(player, PlayerSnapshot.capture(player), channel, message);
        player.sendMessage(plugin.formats().render(context, args[1]));
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        plugin.messages().send(sender, "player-only");
        return null;
    }

    private void sendMini(CommandSender sender, String value) {
        if (value != null && !value.isBlank()) sender.sendMessage(plugin.text().parseRaw(value));
    }

    private static String replace(String input, Map<String, String> values) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("help", "channel", "toggle", "friend", "mute", "unmute", "spy", "history", "preview", "reload"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("channel") || args[0].equalsIgnoreCase("toggle"))) {
            return filter(args[1], plugin.channels().all().stream().map(ChatChannel::id).toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("mute") || args[0].equalsIgnoreCase("unmute"))) {
            return filter(args[1], new ArrayList<>(plugin.onlinePlayers().names()));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("preview")) {
            return filter(args[1], new ArrayList<>(plugin.formats().ids()));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("friend")) {
            return filter(args[1], List.of("add", "accept", "deny", "remove", "list", "requests"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("friend")) {
            if (!(sender instanceof Player player)) return List.of();
            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "add" -> filter(args[2], new ArrayList<>(plugin.onlinePlayers().names()));
                case "accept", "deny" -> filter(args[2], plugin.state().incomingFriendRequests(player.getUniqueId()).stream()
                        .map(plugin.state()::knownName).toList());
                case "remove" -> filter(args[2], plugin.state().friendsOf(player.getUniqueId()).stream()
                        .map(plugin.state()::knownName).toList());
                default -> List.of();
            };
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("mute")) {
            return filter(args[2], List.of("30m", "2h", "1d", "7d", "永久"));
        }
        return List.of();
    }

    private static List<String> filter(String prefix, List<String> choices) {
        String needle = prefix.toLowerCase(Locale.ROOT);
        return choices.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(needle)).sorted().toList();
    }
}
