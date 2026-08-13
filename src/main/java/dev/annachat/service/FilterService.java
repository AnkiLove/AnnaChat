package dev.annachat.service;

import dev.annachat.api.ChatFilter;
import dev.annachat.api.FilterResult;
import dev.annachat.api.context.ChatContext;
import dev.annachat.config.ConfiguredFilter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FilterService {
    private volatile List<ConfiguredFilter> configured = List.of();
    private final List<ChatFilter> external = new ArrayList<>();
    private volatile List<ChatFilter> chain = List.of();

    /**
     * 热重载时生成已排序的不可变执行链，聊天热路径无需再次复制和排序。
     */
    public synchronized void apply(List<ConfiguredFilter> filters) {
        configured = List.copyOf(filters);
        rebuild();
    }

    public synchronized void register(ChatFilter filter) {
        external.add(filter);
        rebuild();
    }

    public synchronized void unregister(ChatFilter filter) {
        external.remove(filter);
        rebuild();
    }

    public FilterResult process(ChatContext context) {
        for (ChatFilter filter : chain) {
            FilterResult result = filter.filter(context);
            if (result == null || result.action() == FilterResult.Action.ALLOW) continue;
            if (result.action() == FilterResult.Action.REPLACE) {
                context.message(result.message());
                continue;
            }
            return result;
        }
        return FilterResult.allow();
    }

    private void rebuild() {
        List<ChatFilter> rebuilt = new ArrayList<>(configured);
        rebuilt.addAll(external);
        rebuilt.sort(Comparator.comparingInt(ChatFilter::priority));
        chain = List.copyOf(rebuilt);
    }
}
