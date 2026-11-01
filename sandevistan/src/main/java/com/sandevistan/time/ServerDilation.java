package com.sandevistan.time;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.IdentityHashMap;
import java.util.Map;

/** Server-side registry of per-world dilation, called from the ServerWorld mixin for every entity tick. */
public final class ServerDilation {
    private ServerDilation() {}

    private static final Map<ServerWorld, WorldDilation> WORLDS = new IdentityHashMap<>();
    private static boolean anyRunning = false;
    private static Entity pendingEntity;
    private static Vec3d pendingVelocity;
    private static WorldDilation pendingWorld;

    public static WorldDilation of(ServerWorld w) {
        return WORLDS.computeIfAbsent(w, k -> new WorldDilation());
    }

    public static void refreshRunning() {
        boolean any = false;
        for (WorldDilation d : WORLDS.values()) {
            if (d.running()) {
                any = true;
                break;
            }
        }
        anyRunning = any;
    }

    public static void clear() {
        WORLDS.clear();
        anyRunning = false;
        pendingEntity = null;
    }

    /** Called at the head of ServerWorld.tickEntity. Returns RUN, SKIP or SCALE (velocity already scaled). */
    public static int decide(ServerWorld w, Entity e) {
        if (!anyRunning) return WorldDilation.RUN;
        WorldDilation d = WORLDS.get(w);
        if (d == null) return WorldDilation.RUN;
        int r = d.decide(e);
        if (r == WorldDilation.SCALE) {
            pendingEntity = e;
            pendingWorld = d;
            pendingVelocity = d.scaleBefore(e);
        }
        return r;
    }

    /** Called at the tail of ServerWorld.tickEntity. */
    public static void afterTick(Entity e) {
        if (pendingEntity == e) {
            pendingWorld.scaleAfter(e, pendingVelocity);
            pendingEntity = null;
        }
    }
}
