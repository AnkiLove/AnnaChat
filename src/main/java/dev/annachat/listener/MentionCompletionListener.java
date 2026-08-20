package dev.annachat.listener;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import dev.annachat.service.MentionService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatTabCompleteEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 为普通聊天输入中的 @玩家 提供客户端 Tab 补全。
 */
@SuppressWarnings("deprecation")
public final class MentionCompletionListener implements Listener {
    private final MentionService mentions;

    public MentionCompletionListener(MentionService mentions) {
        this.mentions = mentions;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (event.isCommand() || !(event.getSender() instanceof Player player)) return;
        String token = lastToken(event.getBuffer());
        List<String> matches = mentions.complete(token, player.getUniqueId());
        if (matches.isEmpty()) return;

        // @ 补全必须完全接管列表，避免客户端优先选中服务端默认提供的裸玩家名。
        event.setCompletions(new ArrayList<>(matches));
        event.setHandled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLegacyChatTabComplete(PlayerChatTabCompleteEvent event) {
        String token = event.getLastToken();
        List<String> matches = mentions.complete(token, event.getPlayer().getUniqueId());
        if (!matches.isEmpty()) {
            event.getTabCompletions().clear();
            event.getTabCompletions().addAll(matches);
        }
    }

    static String lastToken(String input) {
        if (input == null || input.isEmpty()) return "";
        int separator = Math.max(input.lastIndexOf(' '), input.lastIndexOf('\t'));
        return input.substring(separator + 1);
    }
}
