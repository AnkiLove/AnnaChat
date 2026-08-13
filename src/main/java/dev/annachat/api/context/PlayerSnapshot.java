package dev.annachat.api.context;

import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public record PlayerSnapshot(
        UUID uniqueId,
        String name,
        String displayName,
        Key worldKey,
        double x,
        double y,
        double z
) {
    public static PlayerSnapshot capture(Player player) {
        Location location = player.getLocation();
        return new PlayerSnapshot(
                player.getUniqueId(),
                player.getName(),
                player.getName(),
                player.getWorld().getKey(),
                location.getX(),
                location.getY(),
                location.getZ()
        );
    }

    public double distanceSquared(Location location) {
        if (!worldKey.equals(location.getWorld().getKey())) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = x - location.getX();
        double dy = y - location.getY();
        double dz = z - location.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
