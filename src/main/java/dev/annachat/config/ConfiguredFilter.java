package dev.annachat.config;

import dev.annachat.api.ChatFilter;
import dev.annachat.api.FilterResult;
import dev.annachat.api.context.ChatContext;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfiguredFilter implements ChatFilter {
    public enum Type { CONTAINS, REGEX }

    private final int priority;
    private final Type type;
    private final String needle;
    private final Pattern pattern;
    private final FilterResult.Action action;
    private final String replacement;
    private final String reason;
    private final boolean ignoreCase;

    public ConfiguredFilter(int priority, Type type, String value, boolean ignoreCase,
                            FilterResult.Action action, String replacement, String reason) {
        this.priority = priority;
        this.type = type;
        this.ignoreCase = ignoreCase;
        this.needle = ignoreCase ? value.toLowerCase(Locale.ROOT) : value;
        this.pattern = type == Type.REGEX
                ? Pattern.compile(value, ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0)
                : null;
        this.action = action;
        this.replacement = replacement;
        this.reason = reason;
    }

    @Override
    public FilterResult filter(ChatContext context) {
        String message = context.message();
        boolean matches = type == Type.CONTAINS
                ? (ignoreCase ? message.toLowerCase(Locale.ROOT) : message).contains(needle)
                : pattern.matcher(message).find();
        if (!matches) return FilterResult.allow();
        return switch (action) {
            case ALLOW -> FilterResult.allow();
            case BLOCK -> FilterResult.block(reason);
            case SHADOW -> FilterResult.shadow(reason);
            case REPLACE -> {
                String output;
                if (type == Type.REGEX) {
                    Matcher matcher = pattern.matcher(message);
                    output = matcher.replaceAll(Matcher.quoteReplacement(replacement));
                } else if (ignoreCase) {
                    output = Pattern.compile(Pattern.quote(needle), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                            .matcher(message).replaceAll(Matcher.quoteReplacement(replacement));
                } else {
                    output = message.replace(needle, replacement);
                }
                yield FilterResult.replace(output, reason);
            }
        };
    }

    @Override
    public int priority() {
        return priority;
    }
}
