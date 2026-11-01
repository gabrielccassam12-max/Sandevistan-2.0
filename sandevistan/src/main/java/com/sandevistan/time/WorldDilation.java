package com.sandevistan.time;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The heart of the mod: the time-dilation state of ONE world, used identically by the server and the client.
 *
 * Every entity is asked once per tick "do you run this tick?":
 *  - normal entities (mobs, items, TNT...) run their whole tick() only a fraction of the time (an accumulator
 *    per entity), so AI, attack timers, physics, burning, potion effects... all genuinely slow down;
 *  - projectiles run EVERY tick but with scaled velocity (gravity and acceleration scaled too), so arrows and
 *    fireballs crawl smoothly through the air instead of teleporting in jumps;
 *  - players, the implant owners, anything the owners ride and anything the owners shot are never slowed.
 */
public class WorldDilation {
    public static final int RUN = 0, SKIP = 1, SCALE = 2;
    public static final int MODE_ALL = 0, MODE_MOBS = 1, MODE_SELECTED = 2;

    /** Factor applied right now (1 = normal time) and the factor it is moving towards. */
    public float current = 1f, target = 1f;
    public final IntOpenHashSet owners = new IntOpenHashSet();
    public int mode = MODE_ALL;
    public final Set<String> selected = new HashSet<>();
    public final Set<String> excluded = new HashSet<>();
    public boolean projectiles = true;

    private final Int2FloatOpenHashMap acc = new Int2FloatOpenHashMap();
    private static final Map<EntityType<?>, String> NAMES = new IdentityHashMap<>();

    public boolean running() {
        return current < 0.999f || target < 0.999f;
    }

    /** Falls fast (the world freezes almost instantly) and recovers slowly (time returns gradually). */
    public void tickRamp() {
        if (target < current) current = Math.max(target, current - 0.3f);
        else if (target > current) current = Math.min(target, current + 0.04f);
        if (current >= 0.999f && target >= 0.999f) {
            current = 1f;
            if (!acc.isEmpty()) acc.clear();
        }
    }

    private static String nameOf(Entity e) {
        return NAMES.computeIfAbsent(e.getType(), t -> Registries.ENTITY_TYPE.getId(t).toString());
    }

    private boolean exempt(Entity e) {
        if (e instanceof PlayerEntity) return true;
        if (owners.contains(e.getId())) return true;
        if (e.hasPlayerRider()) return true;
        if (e instanceof ProjectileEntity p) {
            Entity o = p.getOwner();
            return o != null && owners.contains(o.getId());
        }
        return false;
    }

    private boolean affects(Entity e) {
        if (!excluded.isEmpty() && excluded.contains(nameOf(e))) return false;
        switch (mode) {
            case MODE_MOBS:
                if (e instanceof MobEntity) return true;
                return e instanceof ProjectileEntity p && p.getOwner() instanceof MobEntity;
            case MODE_SELECTED:
                return selected.contains(nameOf(e));
            default:
                return true;
        }
    }

    /** Decides what this entity does this tick. Never call it twice for the same entity in one tick. */
    public int decide(Entity e) {
        if (current >= 0.999f) return RUN;
        if (exempt(e) || !affects(e)) return RUN;
        if (e instanceof ProjectileEntity) return projectiles ? SCALE : RUN;

        int id = e.getId();
        float a = acc.containsKey(id) ? acc.get(id) : ((id * 2654435761L) & 0xFFFF) / 65536f;
        a += current;
        if (a >= 1f) {
            acc.put(id, a - 1f);
            return RUN;
        }
        acc.put(id, a);
        return SKIP;
    }

    /** Real velocity v -> scaled velocity u = f*v, used for the duration of one projectile tick. */
    public Vec3d scaleBefore(Entity e) {
        Vec3d v = e.getVelocity();
        e.setVelocity(v.multiply(current));
        return v;
    }

    /**
     * After the vanilla tick the projectile moved by u and its velocity became u'. We convert back to a real
     * velocity that evolves at the slowed rate: v_new = v + f * (u' - u). Gravity, drag and acceleration
     * therefore also act f times as strong per tick, so the arc of an arrow is the same, only slower.
     */
    public void scaleAfter(Entity e, Vec3d v0) {
        Vec3d u = v0.multiply(current);
        Vec3d u2 = e.getVelocity();
        e.setVelocity(v0.add(u2.subtract(u).multiply(current)));
    }

    public void reset() {
        current = 1f;
        target = 1f;
        owners.clear();
        acc.clear();
    }
}
