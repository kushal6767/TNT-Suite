package com.tntsuite.addon.targeting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Other players' velocity is not reliable client-side, so we measure it ourselves
 * (position change per tick). Used for placement prediction and the anti-waste speed check.
 */
public class MotionTracker {
    private final Map<UUID, Vec3> last = new HashMap<>();
    private final Map<UUID, Vec3> velocity = new HashMap<>();

    /** Call once per tick. */
    public void update() {
        Set<UUID> seen = new HashSet<>();
        for (Player p : mc.level.players()) {
            UUID id = p.getUUID();
            Vec3 pos = p.position();
            Vec3 old = last.put(id, pos);
            if (old != null) velocity.put(id, pos.subtract(old));
            seen.add(id);
        }
        last.keySet().retainAll(seen);
        velocity.keySet().retainAll(seen);
    }

    public Vec3 velocity(Player p) {
        return velocity.getOrDefault(p.getUUID(), Vec3.ZERO);
    }

    /** Horizontal blocks per tick. */
    public double speed(Player p) {
        return velocity(p).horizontalDistance();
    }

    /** Where the player will roughly be in `ticks` ticks (horizontal only). */
    public Vec3 predict(Player p, int ticks) {
        Vec3 v = velocity(p);
        return p.position().add(v.x * ticks, 0, v.z * ticks);
    }

    public void clear() {
        last.clear();
        velocity.clear();
    }
}
