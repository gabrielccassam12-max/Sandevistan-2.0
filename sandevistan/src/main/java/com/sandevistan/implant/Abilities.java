package com.sandevistan.implant;

import com.sandevistan.net.FxPayload;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The active skills. Every one has its own particles, sounds and screen effect. */
public final class Abilities {
    private Abilities() {}

    /** Entry point from the key-press packet. */
    public static void use(ServerPlayerEntity p, Skill skill) {
        if (!SandManager.canUse(p, skill, false)) return;
        ServerWorld w = (ServerWorld) p.getWorld();
        boolean ok = switch (skill) {
            case DASH -> dash(p, w);
            case BARRAGE -> barrage(p, w);
            case SLASH -> slash(p, w);
            case KINETIC -> kinetic(p, w);
            case EXECUTE -> execute(p, w);
            case DODGE -> dodge(p, w, null);
            case SUPERJUMP -> superJump(p, w);
            case RIFT -> rift(p, w);
        };
        if (ok) SandManager.pay(p, skill);
    }

    // ============================================================ DASH RELAMPAGO
    private static boolean dash(ServerPlayerEntity p, ServerWorld w) {
        Vec3d start = p.getPos();
        Vec3d dir = p.getRotationVec(1f);
        Vec3d end = freeDestination(p, w, dir, 9.0);
        if (end.squaredDistanceTo(start) < 1.0) return false;

        trail(w, start, end);
        for (LivingEntity e : along(p, w, start, end, 1.8)) { // shoulder-checks anyone on the way
            e.timeUntilRegen = 0;
            e.damage(w.getDamageSources().playerAttack(p), 3f);
            e.takeKnockback(0.6, p.getX() - e.getX(), p.getZ() - e.getZ());
        }
        ImplantFx.shockRing(w, start, 1.8, ImplantFx.CYAN);
        teleport(p, end);
        ImplantFx.shockRing(w, end, 2.4, ImplantFx.YELLOW);
        Movement.push(p, dir.multiply(0.7));
        iFrames(p, 10);
        SandManager.sfx(w, p, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.9f, 1.7f);
        SandManager.sfx(w, p, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.5f);
        SandManager.fx(p, FxPayload.DASH, 0, 1f);
        return true;
    }

    // ============================================================ BARRAGEM SUPERSONICA
    private static boolean barrage(ServerPlayerEntity p, ServerWorld w) {
        LivingEntity t = target(p, w, 5.0);
        if (t == null) {
            SandManager.note(p, "message.sandevistan.no_target", Formatting.RED);
            return false;
        }
        for (int i = 0; i < 12; i++) {
            final int k = i;
            Scheduler.later(1 + i, () -> {
                if (!p.isAlive() || !t.isAlive() || p.getWorld() != w) return;
                p.swingHand(k % 2 == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND, true);
                t.timeUntilRegen = 0;
                t.damage(w.getDamageSources().playerAttack(p), 3f);
                t.takeKnockback(0.05, p.getX() - t.getX(), p.getZ() - t.getZ());
                Vec3d c = new Vec3d(t.getX(), t.getBodyY(0.3 + 0.4 * w.random.nextDouble()), t.getZ());
                w.spawnParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.25, 0.2, 0.25, 0.3);
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 4, 0.2, 0.2, 0.2, 0.25);
                SandManager.sfx(w, p, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 0.7f, 1.2f + k * 0.04f);
                if (k % 3 == 0) SandManager.fx(p, FxPayload.SHAKE, 3, 0.6f);
            });
        }
        Scheduler.later(14, () -> { // the last blow sends the enemy flying
            if (!p.isAlive() || !t.isAlive() || p.getWorld() != w) return;
            Vec3d dir = Movement.flat(t.getPos().subtract(p.getPos()));
            t.timeUntilRegen = 0;
            t.damage(w.getDamageSources().playerAttack(p), 7f);
            t.takeKnockback(2.4, p.getX() - t.getX(), p.getZ() - t.getZ());
            t.addVelocity(dir.x * 0.8, 0.5, dir.z * 0.8);
            t.velocityModified = true;
            w.spawnParticles(ParticleTypes.EXPLOSION, t.getX(), t.getBodyY(0.5), t.getZ(), 1, 0, 0, 0, 0);
            ImplantFx.shockRing(w, t.getPos(), 2.5, ImplantFx.YELLOW);
            SandManager.sfx(w, p, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.8f);
            SandManager.fx(p, FxPayload.SHAKE, 8, 1.4f);
        });
        return true;
    }

    // ============================================================ CORTE TEMPORAL
    private static boolean slash(ServerPlayerEntity p, ServerWorld w) {
        Vec3d start = p.getPos();
        Vec3d dir = Movement.flat(p.getRotationVec(1f));
        Vec3d end = freeDestination(p, w, dir, 13.0);
        if (end.squaredDistanceTo(start) < 2.0) return false;

        List<LivingEntity> victims = along(p, w, start, end, 2.2);
        trail(w, start.add(0, 1, 0), end.add(0, 1, 0));
        ImplantFx.line(w, start.add(0, 1.4, 0), end.add(0, 1.4, 0), ParticleTypes.END_ROD, 0.4);
        teleport(p, end);
        iFrames(p, 14);
        SandManager.sfx(w, p, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
        SandManager.fx(p, FxPayload.DASH, 1, 1f);

        // the cuts "catch up": every victim is hit at nearly the same instant, a moment later
        Scheduler.later(9, () -> {
            for (LivingEntity e : victims) {
                if (!e.isAlive()) continue;
                e.timeUntilRegen = 0;
                e.damage(w.getDamageSources().playerAttack(p), 14f);
                e.takeKnockback(0.5, p.getX() - e.getX(), p.getZ() - e.getZ());
                for (int i = 0; i < 3; i++) {
                    w.spawnParticles(ParticleTypes.SWEEP_ATTACK, e.getX() + (w.random.nextDouble() - 0.5), e.getBodyY(0.3 + 0.3 * i),
                            e.getZ() + (w.random.nextDouble() - 0.5), 1, 0, 0, 0, 0);
                }
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getBodyY(0.5), e.getZ(), 14, 0.3, 0.4, 0.3, 0.4);
            }
            if (!victims.isEmpty()) {
                SandManager.sfx(w, p, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f);
                SandManager.fx(p, FxPayload.SHAKE, 6, 1.0f);
            }
        });
        return true;
    }

    // ============================================================ IMPACTO CINETICO
    private static boolean kinetic(ServerPlayerEntity p, ServerWorld w) {
        Vec3d dir = Movement.flat(p.getRotationVec(1f));
        Movement.push(p, new Vec3d(dir.x * 1.0, 0.22, dir.z * 1.0));
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.3, p.getZ(), 18, 0.2, 0.3, 0.2, 0.3); // legs charging
        SandManager.sfx(w, p, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 1.5f);
        Scheduler.later(5, () -> {
            if (!p.isAlive() || p.getWorld() != w) return;
            Vec3d c = p.getPos().add(Movement.flat(p.getRotationVec(1f)).multiply(1.6));
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(3.2),
                    e -> e != p && e.isAlive() && e.squaredDistanceTo(c) <= 3.4 * 3.4)) {
                e.timeUntilRegen = 0;
                if (e.damage(w.getDamageSources().playerAttack(p), 18f)) {
                    Vec3d away = Movement.flat(e.getPos().subtract(p.getPos()));
                    e.takeKnockback(1.8, p.getX() - e.getX(), p.getZ() - e.getZ());
                    e.addVelocity(away.x * 0.5, 0.55, away.z * 0.5);
                    e.velocityModified = true;
                }
            }
            BlockState below = w.getBlockState(p.getBlockPos().down());
            if (below.isAir()) below = Blocks.STONE.getDefaultState();
            BlockStateParticleEffect crack = new BlockStateParticleEffect(ParticleTypes.BLOCK, below);
            for (int i = 0; i < 20; i++) {
                double a = i * Math.PI * 2 / 20;
                w.spawnParticles(crack, c.x + Math.cos(a) * 1.8, p.getY() + 0.2, c.z + Math.sin(a) * 1.8, 3, 0.15, 0.2, 0.15, 0.2);
            }
            ImplantFx.shockRing(w, new Vec3d(c.x, p.getY(), c.z), 3.0, ImplantFx.YELLOW);
            w.spawnParticles(ParticleTypes.EXPLOSION, c.x, p.getY() + 0.5, c.z, 1, 0, 0, 0, 0);
            SandManager.sfx(w, p, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.3f);
            SandManager.sfx(w, p, SoundEvents.BLOCK_ANVIL_LAND, 0.8f, 0.6f);
            SandManager.fx(p, FxPayload.IMPACT, 0, 1f);
            SandManager.fx(p, FxPayload.SHAKE, 8, 1.6f);
        });
        return true;
    }

    // ============================================================ EXECUCAO RELAMPAGO
    private static boolean execute(ServerPlayerEntity p, ServerWorld w) {
        float threshold = SandManager.tierOf(p) == ImplantTier.ARASAKA ? 0.5f : 0.35f;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(8),
                e -> e != p && e.isAlive() && !e.isSpectator()
                        && (e.getHealth() <= e.getMaxHealth() * threshold || e.getHealth() <= 8f))) {
            double d = e.squaredDistanceTo(p);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best == null) {
            SandManager.note(p, "message.sandevistan.no_weak_target", Formatting.RED);
            return false;
        }
        final LivingEntity t = best;
        Vec3d behindDir = Movement.flat(t.getRotationVec(1f)).multiply(-1.6);
        Vec3d pos = t.getPos().add(behindDir);
        if (!w.isSpaceEmpty(p, p.getBoundingBox().offset(pos.subtract(p.getPos())))) pos = t.getPos().add(0, 0, 0).add(behindDir.multiply(-1));
        trail(w, p.getPos().add(0, 1, 0), pos.add(0, 1, 0));
        teleport(p, pos);
        p.setYaw((float) (Math.toDegrees(Math.atan2(t.getZ() - pos.z, t.getX() - pos.x)) - 90.0));
        iFrames(p, 30);
        SandManager.fx(p, FxPayload.CAMERA, t.getId(), 26f); // the camera follows the finishing move
        SandManager.sfx(w, p, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f, 1.9f);

        for (int i = 0; i < 5; i++) {
            final int k = i;
            Scheduler.later(3 + i * 2, () -> {
                if (!t.isAlive() || !p.isAlive()) return;
                p.swingHand(k % 2 == 0 ? Hand.MAIN_HAND : Hand.OFF_HAND, true);
                t.timeUntilRegen = 0;
                t.damage(w.getDamageSources().playerAttack(p), 2f);
                w.spawnParticles(ParticleTypes.SWEEP_ATTACK, t.getX(), t.getBodyY(0.4 + 0.1 * k), t.getZ(), 1, 0.2, 0.2, 0.2, 0);
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getBodyY(0.5), t.getZ(), 8, 0.3, 0.3, 0.3, 0.3);
                SandManager.sfx(w, p, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.3f + k * 0.1f);
            });
        }
        Scheduler.later(15, () -> {
            if (!t.isAlive()) return;
            t.timeUntilRegen = 0;
            t.damage(w.getDamageSources().playerAttack(p), t.getHealth() + 20f);
            w.spawnParticles(ParticleTypes.EXPLOSION, t.getX(), t.getBodyY(0.5), t.getZ(), 1, 0, 0, 0, 0);
            ImplantFx.shockRing(w, t.getPos(), 2.4, ImplantFx.YELLOW);
            SandManager.sfx(w, p, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
            SandManager.sfx(w, p, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.6f);
            SandManager.fx(p, FxPayload.SHAKE, 6, 1.2f);
        });
        return true;
    }

    // ============================================================ ESQUIVA PERFEITA
    /** Sidestep. dangerDir = direction the incoming projectile is travelling (null = manual dodge). */
    private static boolean dodge(ServerPlayerEntity p, ServerWorld w, Vec3d dangerDir) {
        Vec3d base = dangerDir != null ? dangerDir : p.getRotationVec(1f);
        Vec3d side = Movement.flat(base).crossProduct(new Vec3d(0, 1, 0)).normalize();
        Vec3d[] options = {side, side.multiply(-1), Movement.flat(base).multiply(-1)};
        Vec3d dest = null;
        Vec3d from = p.getPos();
        for (Vec3d o : options) {
            Vec3d d = freeDestination(p, w, o, 3.5);
            if (d.squaredDistanceTo(from) > 2.0) {
                dest = d;
                break;
            }
        }
        if (dest == null) return false;
        ImplantFx.line(w, from.add(0, 1, 0), dest.add(0, 1, 0), ImplantFx.CYAN, 0.35);
        w.spawnParticles(ParticleTypes.END_ROD, from.x, from.y + 1, from.z, 12, 0.3, 0.6, 0.3, 0.05); // the afterimage
        teleport(p, dest);
        iFrames(p, 12);
        SandManager.sfx(w, p, SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.5f);
        SandManager.fx(p, FxPayload.DODGE, 0, 1f);
        return true;
    }

    /** Every tick while active: if something dangerous is about to hit, sidestep automatically. */
    public static void autoDodge(ServerPlayerEntity p, SandState s) {
        if (s.dodgeLock > 0 || s.skillCd[Skill.DODGE.ordinal()] > 0) return;
        if (!SandManager.canUse(p, Skill.DODGE, true)) return;
        ServerWorld w = (ServerWorld) p.getWorld();
        Vec3d c = p.getPos().add(0, 1.0, 0);
        for (ProjectileEntity pr : w.getEntitiesByClass(ProjectileEntity.class, p.getBoundingBox().expand(6),
                pr -> pr.getOwner() != p)) {
            Vec3d v = pr.getVelocity();
            if (v.lengthSquared() < 1e-4) continue;
            Vec3d vn = v.normalize();
            Vec3d to = c.subtract(pr.getPos());
            double along = to.dotProduct(vn);
            if (along < 0 || along > 4.0) continue;
            double miss = to.subtract(vn.multiply(along)).length();
            if (miss < 1.3 && dodge(p, w, vn)) {
                SandManager.pay(p, Skill.DODGE);
                s.dodgeLock = 10;
                return;
            }
        }
    }

    // ============================================================ SUPER SALTO
    private static boolean superJump(ServerPlayerEntity p, ServerWorld w) {
        Vec3d dir = Movement.flat(p.getRotationVec(1f));
        Movement.push(p, new Vec3d(dir.x * 0.55, 1.45, dir.z * 0.55));
        p.fallDistance = 0;
        ImplantFx.shockRing(w, p.getPos(), 2.6, ImplantFx.CYAN);
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.2, p.getZ(), 24, 0.3, 0.1, 0.3, 0.35);
        SandManager.sfx(w, p, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.4f, 1.9f);
        SandManager.fx(p, FxPayload.SHAKE, 3, 0.7f);
        return true;
    }

    // ============================================================ FENDA TEMPORAL (Arasaka)
    private static boolean rift(ServerPlayerEntity p, ServerWorld w) {
        SandState s = SandManager.state(p);
        s.riftTicks = 60; // the world's factor drops to zero for three seconds
        ImplantFx.shockRing(w, p.getPos(), 6.0, ImplantFx.YELLOW);
        ImplantFx.shockRing(w, p.getPos(), 3.5, ImplantFx.CYAN);
        w.spawnParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0, 0, 0, 0);
        SandManager.sfx(w, p, SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 0.6f);
        SandManager.sfx(w, p, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 0.5f);
        SandManager.fx(p, FxPayload.SHAKE, 10, 1.0f);
        SandManager.note(p, "message.sandevistan.rift", Formatting.GOLD);
        return true;
    }

    /** When the rift closes, everything it held still takes the accumulated shock. */
    public static void riftEnd(ServerPlayerEntity p) {
        ServerWorld w = (ServerWorld) p.getWorld();
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(14),
                e -> e != p && e.isAlive())) {
            e.timeUntilRegen = 0;
            e.damage(w.getDamageSources().playerAttack(p), 12f);
            w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getBodyY(0.5), e.getZ(), 16, 0.3, 0.5, 0.3, 0.4);
        }
        ImplantFx.shockRing(w, p.getPos(), 8.0, ImplantFx.YELLOW);
        SandManager.sfx(w, p, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.0f);
        SandManager.fx(p, FxPayload.SHAKE, 10, 1.6f);
    }

    // ============================================================ helpers
    private static void teleport(ServerPlayerEntity p, Vec3d pos) {
        p.networkHandler.requestTeleport(pos.x, pos.y, pos.z, p.getYaw(), p.getPitch());
        p.fallDistance = 0;
    }

    private static void iFrames(ServerPlayerEntity p, int ticks) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, ticks, 4, true, false, false));
    }

    private static void trail(ServerWorld w, Vec3d a, Vec3d b) {
        ImplantFx.line(w, a.add(0, 0.9, 0), b.add(0, 0.9, 0), ImplantFx.CYAN, 0.3);
        ImplantFx.line(w, a.add(0, 0.2, 0), b.add(0, 0.2, 0), ImplantFx.YELLOW, 0.6);
        Vec3d d = b.subtract(a);
        int n = Math.max(1, (int) (d.length() / 1.2));
        for (int i = 0; i <= n; i++) {
            Vec3d q = a.add(d.multiply(i / (double) n));
            w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y + 1.0, q.z, 2, 0.25, 0.5, 0.25, 0.15);
        }
    }

    /** Furthest point along dir (up to max blocks) that the player fits in, stopping at the first obstacle. */
    private static Vec3d freeDestination(ServerPlayerEntity p, ServerWorld w, Vec3d dir, double max) {
        Vec3d start = p.getPos();
        Vec3d best = start;
        for (double d = 0.5; d <= max; d += 0.5) {
            Vec3d off = dir.multiply(d);
            if (w.isSpaceEmpty(p, p.getBoundingBox().offset(off))) best = start.add(off);
            else break;
        }
        return best;
    }

    private static List<LivingEntity> along(ServerPlayerEntity p, ServerWorld w, Vec3d a, Vec3d b, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        Vec3d ab = b.subtract(a);
        double len2 = Math.max(1e-6, ab.lengthSquared());
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(a, b).expand(radius + 1),
                e -> e != p && e.isAlive() && !e.isSpectator())) {
            double t = Math.max(0, Math.min(1, e.getPos().subtract(a).dotProduct(ab) / len2));
            Vec3d closest = a.add(ab.multiply(t));
            if (closest.distanceTo(e.getPos()) <= radius && seen.add(e.getId())) out.add(e);
        }
        return out;
    }

    /** The entity under the crosshair, or the closest living thing in front of the player. */
    private static LivingEntity target(ServerPlayerEntity p, ServerWorld w, double range) {
        Vec3d eye = p.getEyePos();
        Vec3d end = eye.add(p.getRotationVec(1f).multiply(range));
        EntityHitResult hit = ProjectileUtil.raycast(p, eye, end, new Box(eye, end).expand(1.0),
                e -> !e.isSpectator() && e.canHit(), range * range);
        if (hit != null && hit.getEntity() instanceof LivingEntity le) return le;
        Vec3d look = Movement.flat(p.getRotationVec(1f));
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(range),
                e -> e != p && e.isAlive() && !e.isSpectator())) {
            Vec3d to = Movement.flat(e.getPos().subtract(p.getPos()));
            if (to.dotProduct(look) < 0.5) continue;
            double d = e.squaredDistanceTo(p);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }
}
