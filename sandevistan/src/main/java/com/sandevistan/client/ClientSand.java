package com.sandevistan.client;

import com.sandevistan.config.SandConfig;
import com.sandevistan.net.FxPayload;
import com.sandevistan.net.HudPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

/** Client-side implant state: HUD numbers, one-shot screen effects, camera shake and heartbeat. */
public final class ClientSand {
    private ClientSand() {}

    public static boolean has;          // an implant is installed
    public static boolean active;
    public static int level, tier;
    public static float energy, stress;
    public static int cooldown, overload, remaining;

    // effect timers (ticks) and smoothed intensity 0..1
    public static float fade;
    public static int flash, dodgeFlash, overloadFx, shake, cameraTicks, cameraTarget;
    public static float shakeAmp;
    private static int beat;

    public static void receiveHud(HudPayload p) {
        has = p.tier() > 0;
        active = p.active();
        level = p.level();
        tier = p.tier();
        energy = p.energy();
        stress = p.stress();
        cooldown = p.cooldown();
        overload = p.overload();
        remaining = p.remaining();
    }

    public static void receiveFx(FxPayload p) {
        MinecraftClient mc = MinecraftClient.getInstance();
        switch (p.type()) {
            case FxPayload.ACTIVATE -> {
                flash = 16;
                play(SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.0f);
                play(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 0.8f);
            }
            case FxPayload.DEACTIVATE -> {
                flash = 10;
                play(SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.0f);
            }
            case FxPayload.DASH -> {
                dodgeFlash = 6;
                shake = Math.max(shake, 3);
                shakeAmp = Math.max(shakeAmp, 0.5f);
            }
            case FxPayload.DODGE -> dodgeFlash = 9;
            case FxPayload.SHAKE -> {
                shake = Math.max(shake, p.a());
                shakeAmp = Math.max(shakeAmp, p.b());
            }
            case FxPayload.CAMERA -> {
                cameraTarget = p.a();
                cameraTicks = (int) p.b();
            }
            case FxPayload.OVERLOAD -> {
                overloadFx = 50;
                shake = 14;
                shakeAmp = 2.0f;
            }
            case FxPayload.RECHARGED -> play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.8f, 0.8f);
            default -> { }
        }
    }

    private static void play(net.minecraft.sound.SoundEvent e, float pitch, float vol) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float v = vol * (float) SandConfig.INSTANCE.soundIntensity;
        if (v > 0.01f) mc.getSoundManager().play(PositionedSoundInstance.master(e, pitch, v));
    }

    public static void reset() {
        has = active = false;
        fade = 0;
        flash = dodgeFlash = overloadFx = shake = cameraTicks = 0;
        energy = stress = 0;
        cooldown = overload = remaining = 0;
    }

    /** Runs once per client tick. */
    public static void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        SandConfig cfg = SandConfig.INSTANCE;

        fade = MathHelper.clamp(fade + (active ? 0.25f : -0.07f), 0f, 1f);
        if (flash > 0) flash--;
        if (dodgeFlash > 0) dodgeFlash--;
        if (overloadFx > 0) overloadFx--;

        // pulsing low heartbeat while the implant is on (faster at higher levels and stress)
        if (active) {
            int period = Math.max(8, (int) (26 - level * 4 - stress * 8));
            if (++beat >= period) {
                beat = 0;
                play(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.55f + stress * 0.3f, 0.7f);
            }
        } else {
            beat = 0;
        }

        // camera shake (barrage, impacts, cyberpsychosis) and the tremor of a stressed implant
        if (cfg.cameraShake) {
            float amp = 0f;
            if (shake > 0) {
                shake--;
                amp = shakeAmp * (shake / 8f + 0.3f);
            } else {
                shakeAmp = 0f;
            }
            if (active && cfg.glitchEffects && stress > 0.65f) amp = Math.max(amp, (stress - 0.65f) * 2.2f);
            if (amp > 0.01f) {
                mc.player.setYaw(mc.player.getYaw() + (mc.player.getRandom().nextFloat() - 0.5f) * amp);
                mc.player.setPitch(mc.player.getPitch() + (mc.player.getRandom().nextFloat() - 0.5f) * amp);
            }
        }

        // the camera follows a finishing move
        if (cameraTicks > 0) {
            cameraTicks--;
            Entity t = mc.world == null ? null : mc.world.getEntityById(cameraTarget);
            if (t != null) {
                double dx = t.getX() - mc.player.getX();
                double dz = t.getZ() - mc.player.getZ();
                double dy = t.getBodyY(0.6) - mc.player.getEyeY();
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
                mc.player.setYaw(MathHelper.lerpAngleDegrees(0.35f, mc.player.getYaw(), yaw));
                mc.player.setPitch(MathHelper.lerp(0.35f, mc.player.getPitch(), pitch));
            }
        }
    }
}
