package dev.annachat.service;

import dev.annachat.model.HistoryEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public final class HistoryService {
    private final ConcurrentLinkedDeque<HistoryEntry> entries = new ConcurrentLinkedDeque<>();
    private volatile int limit = 200;

    public void limit(int limit) {
        this.limit = Math.max(10, limit);
        trim();
    }

    public void add(HistoryEntry entry) {
        entries.addFirst(entry);
        trim();
    }

    public List<HistoryEntry> snapshot() {
        return List.copyOf(new ArrayList<>(entries));
    }

    private void trim() {
        while (entries.size() > limit) {
            entries.pollLast();
        }
    }
}
