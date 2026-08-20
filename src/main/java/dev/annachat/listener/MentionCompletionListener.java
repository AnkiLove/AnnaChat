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

        LinkedHashSet<String> combined = new LinkedHashSet<>(event.getCompletions());
        combined.addAll(matches);
        event.setCompletions(new ArrayList<>(combined));
        event.setHandled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLegacyChatTabComplete(PlayerChatTabCompleteEvent event) {
        event.getTabCompletions().addAll(mentions.complete(
                event.getLastToken(), event.getPlayer().getUniqueId()
        ));
    }

    static String lastToken(String input) {
        if (input == null || input.isEmpty()) return "";
        int separator = Math.max(input.lastIndexOf(' '), input.lastIndexOf('\t'));
        return input.substring(separator + 1);
    }
}
