package com.sandevistan.implant;

import com.sandevistan.config.SandConfig;
import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Parkour layer while the implant is on: sprint trails, extra jump, slide, wall run, wall jump, controlled fall. */
public final class Movement {
    private Movement() {}

    public static void push(ServerPlayerEntity p, Vec3d v) {
        p.setVelocity(v);
        p.velocityModified = true;
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
    }

    public static Vec3d flat(Vec3d v) {
        Vec3d f = new Vec3d(v.x, 0, v.z);
        return f.lengthSquared() < 1e-6 ? new Vec3d(0, 0, 1) : f.normalize();
    }

    /** Called every tick while the implant is active. */
    public static void tick(ServerPlayerEntity p, SandState s, ImplantTier t) {
        ServerWorld w = (ServerWorld) p.getWorld();
        SandConfig cfg = SandConfig.INSTANCE;
        Vec3d vel = p.getVelocity();
        double hSpeed = Math.hypot(vel.x, vel.z);
        boolean ground = p.isOnGround();

        // ---- supersonic run: trails, dust, grass and water reactions
        if (hSpeed > 0.28) {
            double spread = 0.25;
            w.spawnParticles(ImplantFx.CYAN, p.getX(), p.getY() + 0.15, p.getZ(), 2, spread, 0.05, spread, 0.0);
            if (hSpeed > 0.45) {
                w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0.3, 0.6, 0.3, 0.15);
            }
            if (ground && s.activeTicks % 2 == 0) {
                BlockPos below = p.getBlockPos().down();
                BlockState bs = w.getBlockState(below);
                if (!bs.isAir()) {
                    w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, bs), p.getX(), p.getY() + 0.1, p.getZ(),
                            3, 0.3, 0.05, 0.3, 0.1);
                    w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.05, p.getZ(), 1, 0.2, 0.0, 0.2, 0.02);
                }
                BlockState inside = w.getBlockState(p.getBlockPos());
                if (!inside.isAir() && inside.getCollisionShape(w, p.getBlockPos()).isEmpty()) { // grass, flowers, leaves...
                    w.spawnParticles(ParticleTypes.COMPOSTER, p.getX(), p.getY() + 0.4, p.getZ(), 4, 0.35, 0.3, 0.35, 0.05);
                }
            }
            if (p.isTouchingWater()) {
                w.spawnParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.5, p.getZ(), 10, 0.5, 0.2, 0.5, 0.3);
                w.spawnParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + 0.3, p.getZ(), 6, 0.4, 0.2, 0.4, 0.1);
            }
        }

        // ---- extra jump impulse
        if (cfg.enabled("extrajump") && s.prevOnGround && !ground && vel.y > 0.2 && !s.wallRunning) {
            double boost = 0.18 + 0.08 * s.level;
            Vec3d dir = flat(p.getRotationVec(1f)).multiply(hSpeed > 0.1 ? 0.25 * s.level : 0.0);
            push(p, new Vec3d(vel.x + dir.x, vel.y + boost, vel.z + dir.z));
            ImplantFx.shockRing(w, p.getPos(), 1.6, ImplantFx.CYAN);
        }

        // ---- slide
        if (cfg.enabled("slide")) {
            if (s.slideTicks == 0 && s.slideCd == 0 && p.isSneaking() && ground && hSpeed > 0.22) {
                s.slideTicks = 14;
                s.slideCd = 30;
                p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                        net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 0.7f);
            }
            if (s.slideTicks > 0) {
                if (!p.isSneaking() || !ground) {
                    s.slideTicks = 0;
                } else {
                    s.slideTicks--;
                    Vec3d dir = flat(vel.lengthSquared() > 1e-4 ? vel : p.getRotationVec(1f));
                    double sp = Math.min(1.1, Math.max(0.5, hSpeed * 1.08));
                    push(p, new Vec3d(dir.x * sp, Math.min(0, vel.y), dir.z * sp));
                    w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.1, p.getZ(), 3, 0.2, 0.0, 0.2, 0.2);
                    w.spawnParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.2, 0.0, 0.2, 0.1);
                }
            }
        }

        // ---- controlled fall
        if (cfg.enabled("controlledfall") && !ground && !s.wallRunning && vel.y < -0.45) {
            p.fallDistance = Math.min(p.fallDistance, 2.5f);
            if (p.isSneaking()) {
                push(p, new Vec3d(vel.x, Math.max(vel.y, -0.3), vel.z));
                w.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.2, 0.0, 0.2, 0.0);
            }
        }
        if (!s.prevOnGround && ground && p.fallDistance < 0.01f && vel.y < -0.1) {
            ImplantFx.shockRing(w, p.getPos(), 2.2, ImplantFx.CYAN);
        }

        // ---- wall run
        if (cfg.enabled("wallrun") && t.id >= 2) wallRun(p, s, w, vel, hSpeed, ground);

        s.prevOnGround = ground;
    }

    /** Cleanup while the implant is off. */
    public static void idle(ServerPlayerEntity p, SandState s) {
        s.wallRunning = false;
        s.slideTicks = 0;
        s.prevOnGround = p.isOnGround();
    }

    // ------------------------------------------------------------ wall run / wall jump
    private static Direction findWall(ServerPlayerEntity p, ServerWorld w) {
        for (Direction d : Direction.Type.HORIZONTAL) {
            Box b = p.getBoundingBox().offset(d.getOffsetX() * 0.25, 0, d.getOffsetZ() * 0.25);
            if (!w.isSpaceEmpty(p, b)) return d;
        }
        return null;
    }

    private static void wallRun(ServerPlayerEntity p, SandState s, ServerWorld w, Vec3d vel, double hSpeed, boolean ground) {
        if (!s.wallRunning) {
            if (!ground && s.wallCd == 0 && p.isSprinting() && hSpeed > 0.18) {
                Direction wall = findWall(p, w);
                if (wall != null) {
                    s.wallRunning = true;
                    s.wallTicks = 0;
                    s.wallDir = wall;
                    ImplantFx.shockRing(w, p.getPos().add(0, 1, 0), 1.2, ImplantFx.YELLOW);
                }
            }
            return;
        }
        Direction wall = findWall(p, w);
        if (ground || wall == null || s.wallTicks > 45 || p.isSneaking()) {
            s.wallRunning = false;
            s.wallCd = 12;
            return;
        }
        s.wallDir = wall;
        s.wallTicks++;
        Vec3d n = Vec3d.of(wall.getVector());
        Vec3d look = flat(p.getRotationVec(1f));
        Vec3d along = look.subtract(n.multiply(look.dotProduct(n)));
        if (along.lengthSquared() < 0.04) along = flat(vel).subtract(n.multiply(flat(vel).dotProduct(n)));
        if (along.lengthSquared() < 1e-4) {
            s.wallRunning = false;
            return;
        }
        along = along.normalize();
        double sp = 0.38 + 0.12 * s.level;
        push(p, new Vec3d(along.x * sp + n.x * 0.06, 0.03, along.z * sp + n.z * 0.06));
        p.fallDistance = 0;
        BlockPos wallPos = p.getBlockPos().offset(wall);
        BlockState ws = w.getBlockState(wallPos);
        if (!ws.isAir()) {
            w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ws),
                    p.getX() + n.x * 0.4, p.getY() + 1.0, p.getZ() + n.z * 0.4, 3, 0.1, 0.4, 0.1, 0.05);
        }
        w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + n.x * 0.35, p.getY() + 0.9, p.getZ() + n.z * 0.35, 2, 0.1, 0.4, 0.1, 0.1);
    }

    /** Jump pressed in mid-air: kick off the wall if wall-running. */
    public static void jumpPressed(ServerPlayerEntity p, SandState s) {
        if (!s.active || !s.wallRunning || s.wallDir == null) return;
        Vec3d n = Vec3d.of(s.wallDir.getVector()).multiply(-1); // away from the wall
        Vec3d look = flat(p.getRotationVec(1f));
        double power = 0.55 + 0.12 * s.level;
        push(p, new Vec3d(n.x * 0.7 + look.x * power * 0.6, 0.78 + 0.05 * s.level, n.z * 0.7 + look.z * power * 0.6));
        s.wallRunning = false;
        s.wallCd = 14;
        p.fallDistance = 0;
        ServerWorld w = (ServerWorld) p.getWorld();
        ImplantFx.shockRing(w, p.getPos().add(0, 1, 0), 1.8, ImplantFx.YELLOW);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 1.8f);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 8, 0, true, false, false));
    }
}
