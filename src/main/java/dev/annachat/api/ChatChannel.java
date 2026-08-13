package dev.annachat.api;

public interface ChatChannel {
    String id();
    String displayName();
    String formatId();
    String permission();
    String receivePermission();
    AudienceType audienceType();
    double radius();
    boolean sameWorld();
    long cooldownMillis();
    int priority();
    boolean enabled();
}
