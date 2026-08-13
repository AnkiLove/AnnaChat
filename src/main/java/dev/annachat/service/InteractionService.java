package dev.annachat.service;

import dev.annachat.api.InteractionProvider;
import dev.annachat.api.InteractiveMatch;
import dev.annachat.api.context.ChatContext;
import dev.annachat.config.ConfiguredInteraction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class InteractionService {
    private final TextService textService;
    private volatile List<ConfiguredInteraction> configured = List.of();
    private final List<InteractionProvider> external = new ArrayList<>();
    private volatile List<InteractionProvider> providers = List.of();

    public InteractionService(TextService textService) {
        this.textService = textService;
    }

    /**
     * 配置切换时生成已排序快照，避免每条聊天消息重新排序交互规则。
     */
    public synchronized void apply(List<ConfiguredInteraction> interactions) {
        configured = List.copyOf(interactions);
        rebuild();
    }

    public synchronized void register(InteractionProvider provider) {
        external.add(provider);
        rebuild();
    }

    public synchronized void unregister(InteractionProvider provider) {
        external.remove(provider);
        rebuild();
    }

    public Component render(ChatContext context, String style) {
        String message = context.message();
        TextComponent.Builder output = Component.text();
        int cursor = 0;
        while (cursor < message.length()) {
            InteractiveMatch best = null;
            int bestPriority = Integer.MAX_VALUE;
            for (InteractionProvider provider : providers) {
                InteractiveMatch match = provider.find(context, message, cursor).orElse(null);
                if (match == null || match.start() < cursor || match.end() > message.length()) continue;
                if (best == null || match.start() < best.start()
                        || (match.start() == best.start() && provider.priority() < bestPriority)) {
                    best = match;
                    bestPriority = provider.priority();
                }
            }
            if (best == null) {
                output.append(textService.playerText(context, message.substring(cursor), style));
                break;
            }
            if (best.start() > cursor) {
                output.append(textService.playerText(context, message.substring(cursor, best.start()), style));
            }
            output.append(best.component());
            cursor = best.end();
        }
        if (message.isEmpty()) return Component.empty();
        return output.build();
    }

    private void rebuild() {
        List<InteractionProvider> rebuilt = new ArrayList<>(configured);
        rebuilt.addAll(external);
        rebuilt.sort(Comparator.comparingInt(InteractionProvider::priority));
        providers = List.copyOf(rebuilt);
    }
}
