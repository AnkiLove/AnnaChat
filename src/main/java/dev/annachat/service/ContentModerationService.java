package dev.annachat.service;

import dev.annachat.api.ModerationMatch;
import dev.annachat.config.ModerationSettings;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 负责脏话、涉政言论等分类词库的高效匹配。
 *
 * <p>热重载时先构造完整的不可变 Trie 快照，再通过一次 volatile 写入替换，
 * 因此 Paper 主线程和 Folia 多区域线程都不会观察到半加载词库。匹配前使用
 * Unicode NFKC 归一化，并可忽略空白、标点及管理员指定字符，从而识别常见
 * 的全角字符和插入符号绕过方式。</p>
 */
public final class ContentModerationService {
    private volatile Snapshot snapshot = Snapshot.disabled();
    private final Map<UUID, WarningState> warnings = new ConcurrentHashMap<>();
    private final AtomicLong nextWarningCleanupAt = new AtomicLong();

    /**
     * 编译并原子应用新的审核配置。
     */
    public void apply(ModerationSettings settings) {
        snapshot = Snapshot.compile(settings);
        warnings.clear();
        nextWarningCleanupAt.set(0L);
    }

    /**
     * 对文本执行只读匹配。该方法不访问 Bukkit 实体，可从任意线程调用。
     */
    public Optional<ModerationMatch> inspect(String message) {
        return snapshot.inspect(message);
    }

    /**
     * 记录一次玩家警告，并返回当前警告窗口中的累计次数。
     */
    public int recordWarning(UUID playerId) {
        long now = System.currentTimeMillis();
        long windowMillis = snapshot.warningResetSeconds * 1000L;
        int count = warnings.compute(playerId, (ignored, previous) -> {
            if (previous == null || now - previous.lastWarningAt >= windowMillis) {
                return new WarningState(1, now);
            }
            return new WarningState(previous.count + 1, now);
        }).count;
        // 每分钟最多清理一次过期状态，避免长期运行服务器按历史 UUID 无限增长。
        long cleanupAt = nextWarningCleanupAt.get();
        if (now >= cleanupAt && nextWarningCleanupAt.compareAndSet(cleanupAt, now + 60_000L)) {
            warnings.entrySet().removeIf(entry -> now - entry.getValue().lastWarningAt >= windowMillis);
        }
        return count;
    }

    public boolean enabled() {
        return snapshot.enabled;
    }

    public String bypassPermission() {
        return snapshot.bypassPermission;
    }

    public boolean consoleNotify() {
        return snapshot.consoleNotify;
    }

    public int categoryCount() {
        return snapshot.categoryCount;
    }

    public int wordCount() {
        return snapshot.wordCount;
    }

    private record WarningState(int count, long lastWarningAt) {
    }

    private static final class Snapshot {
        private final boolean enabled;
        private final String bypassPermission;
        private final boolean ignoreWhitespace;
        private final boolean ignorePunctuation;
        private final Set<Integer> ignoredCodePoints;
        private final int warningResetSeconds;
        private final boolean consoleNotify;
        private final TrieNode root;
        private final int categoryCount;
        private final int wordCount;

        private Snapshot(boolean enabled, String bypassPermission, boolean ignoreWhitespace,
                         boolean ignorePunctuation, Set<Integer> ignoredCodePoints,
                         int warningResetSeconds, boolean consoleNotify, TrieNode root,
                         int categoryCount, int wordCount) {
            this.enabled = enabled;
            this.bypassPermission = bypassPermission;
            this.ignoreWhitespace = ignoreWhitespace;
            this.ignorePunctuation = ignorePunctuation;
            this.ignoredCodePoints = ignoredCodePoints;
            this.warningResetSeconds = warningResetSeconds;
            this.consoleNotify = consoleNotify;
            this.root = root;
            this.categoryCount = categoryCount;
            this.wordCount = wordCount;
        }

        private static Snapshot disabled() {
            return new Snapshot(false, "annachat.bypass.moderation", true, true,
                    Set.of(), 300, true, new TrieNode(), 0, 0);
        }

        private static Snapshot compile(ModerationSettings settings) {
            TrieNode root = new TrieNode();
            int words = 0;
            for (ModerationSettings.Category category : settings.categories()) {
                List<int[]> whitelist = category.whitelist().stream()
                        .map(value -> normalize(value, settings.ignoreWhitespace(),
                                settings.ignorePunctuation(), settings.ignoredCodePoints()))
                        .filter(value -> value.length > 0)
                        .toList();
                for (String word : category.words()) {
                    int[] normalized = normalize(word, settings.ignoreWhitespace(),
                            settings.ignorePunctuation(), settings.ignoredCodePoints());
                    if (normalized.length == 0) continue;
                    TrieNode cursor = root;
                    for (int codePoint : normalized) {
                        cursor = cursor.children.computeIfAbsent(codePoint, ignored -> new TrieNode());
                    }
                    cursor.entries.add(new Entry(category.id(), category.displayName(),
                            category.priority(), word, category.reason(), whitelist));
                    words++;
                }
            }
            return new Snapshot(settings.enabled(), settings.bypassPermission(),
                    settings.ignoreWhitespace(), settings.ignorePunctuation(),
                    settings.ignoredCodePoints(), settings.warningResetSeconds(),
                    settings.consoleNotify(), root, settings.categories().size(), words);
        }

        private Optional<ModerationMatch> inspect(String message) {
            if (!enabled || message == null || message.isBlank() || wordCount == 0) return Optional.empty();
            int[] normalized = normalize(message, ignoreWhitespace, ignorePunctuation, ignoredCodePoints);
            for (int start = 0; start < normalized.length; start++) {
                TrieNode cursor = root;
                List<Candidate> candidates = new ArrayList<>();
                for (int end = start; end < normalized.length; end++) {
                    cursor = cursor.children.get(normalized[end]);
                    if (cursor == null) break;
                    for (Entry entry : cursor.entries) {
                        if (!coveredByWhitelist(normalized, start, end + 1, entry.whitelist)) {
                            candidates.add(new Candidate(entry, end - start + 1));
                        }
                    }
                }
                if (!candidates.isEmpty()) {
                    Candidate selected = candidates.stream()
                            .min(Comparator.comparingInt((Candidate value) -> value.entry.priority)
                                    .thenComparing(Comparator.comparingInt(Candidate::length).reversed()))
                            .orElseThrow();
                    Entry entry = selected.entry;
                    return Optional.of(new ModerationMatch(
                            entry.categoryId, entry.categoryDisplayName, entry.word, entry.reason));
                }
            }
            return Optional.empty();
        }

        private static boolean coveredByWhitelist(int[] message, int matchStart, int matchEnd,
                                                  List<int[]> whitelist) {
            for (int[] phrase : whitelist) {
                if (phrase.length < matchEnd - matchStart) continue;
                for (int start = 0; start + phrase.length <= message.length; start++) {
                    boolean equal = true;
                    for (int index = 0; index < phrase.length; index++) {
                        if (message[start + index] != phrase[index]) {
                            equal = false;
                            break;
                        }
                    }
                    if (equal && matchStart >= start && matchEnd <= start + phrase.length) return true;
                }
            }
            return false;
        }
    }

    private static int[] normalize(String input, boolean ignoreWhitespace,
                                   boolean ignorePunctuation, Set<Integer> ignoredCodePoints) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return normalized.codePoints().filter(codePoint -> {
            if (ignoredCodePoints.contains(codePoint)) return false;
            if (ignoreWhitespace && Character.isWhitespace(codePoint)) return false;
            return !ignorePunctuation || !isPunctuation(codePoint);
        }).toArray();
    }

    private static boolean isPunctuation(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION,
                 Character.DASH_PUNCTUATION,
                 Character.START_PUNCTUATION,
                 Character.END_PUNCTUATION,
                 Character.INITIAL_QUOTE_PUNCTUATION,
                 Character.FINAL_QUOTE_PUNCTUATION,
                 Character.OTHER_PUNCTUATION,
                 Character.MATH_SYMBOL,
                 Character.CURRENCY_SYMBOL,
                 Character.MODIFIER_SYMBOL,
                 Character.OTHER_SYMBOL -> true;
            default -> false;
        };
    }

    private static final class TrieNode {
        private final Map<Integer, TrieNode> children = new HashMap<>();
        private final List<Entry> entries = new ArrayList<>();
    }

    private record Entry(String categoryId, String categoryDisplayName, int priority,
                         String word, String reason, List<int[]> whitelist) {
    }

    private record Candidate(Entry entry, int length) {
    }
}
