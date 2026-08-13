package dev.annachat.model;

public record MuteEntry(long expiresAt, String reason) {
    public boolean permanent() {
        return expiresAt <= 0;
    }

    public boolean expired(long now) {
        return !permanent() && expiresAt <= now;
    }
}
