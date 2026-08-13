package dev.annachat.listener;

import dev.annachat.AnnaChat;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * 在服务端控制台命令进入 Minecraft 命令解析器前接管 AnnaChat 命令。
 *
 * <p>部分 26.x 服务端构建会为控制台创建不含世界的命令源，随后在命令
 * 解析阶段访问世界规则并抛出异常。本监听器仅拦截 AnnaChat 自身的命令，
 * 再直接调用 Bukkit 的 {@link PluginCommand}，因此不会改变其他插件的命令
 * 行为，也不会绕过 AnnaChat 原有的权限检查与参数校验。</p>
 */
public final class ConsoleCommandBridge implements Listener {
    private static final Set<String> LABELS = Set.of("annachat", "ac", "achat");

    private final AnnaChat plugin;

    public ConsoleCommandBridge(AnnaChat plugin) {
        this.plugin = plugin;
    }

    /**
     * 以最低优先级尽早取消原始分发，避免有缺陷的服务端解析器继续处理。
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onServerCommand(ServerCommandEvent event) {
        String commandLine = event.getCommand().trim();
        if (commandLine.startsWith("/")) commandLine = commandLine.substring(1).trim();
        if (commandLine.isEmpty()) return;

        String[] parts = commandLine.split("\\s+");
        String label = parts[0].toLowerCase(Locale.ROOT);
        if (!LABELS.contains(label)) return;

        PluginCommand command = plugin.getCommand("annachat");
        if (command == null) {
            plugin.getLogger().severe("无法处理控制台命令：plugin.yml 中缺少 annachat 命令");
            return;
        }

        // 先取消服务端的后续解析，再复用正常命令执行器，确保控制台与玩家行为一致。
        event.setCancelled(true);
        String[] arguments = Arrays.copyOfRange(parts, 1, parts.length);
        command.execute(event.getSender(), label, arguments);
    }
}
