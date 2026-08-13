package dev.annachat.api;

public record FilterResult(Action action, String message, String reason) {
    public enum Action {
        ALLOW,
        REPLACE,
        BLOCK,
        SHADOW
    }

    public static FilterResult allow() {
        return new FilterResult(Action.ALLOW, null, null);
    }

    public static FilterResult replace(String message, String reason) {
        return new FilterResult(Action.REPLACE, message, reason);
    }

    public static FilterResult block(String reason) {
        return new FilterResult(Action.BLOCK, null, reason);
    }

    public static FilterResult shadow(String reason) {
        return new FilterResult(Action.SHADOW, null, reason);
    }
}
