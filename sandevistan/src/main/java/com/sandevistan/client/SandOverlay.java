package com.sandevistan.client;

import com.sandevistan.config.SandConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/**
 * Screen effects and HUD, drawn with plain rectangles so nothing depends on shaders:
 * cyan filter, vignette, red/cyan chromatic edges, speed streaks, glitch bars, activation flash, HUD panel.
 */
public final class SandOverlay {
    private SandOverlay() {}

    private static int argb(int a, int r, int g, int b) {
        return (MathHelper.clamp(a, 0, 255) << 24) | (r << 16) | (g << 8) | b;
    }

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden || !ClientSand.has) return;
        SandConfig cfg = SandConfig.INSTANCE;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        float vi = (float) cfg.visualIntensity;
        float k = ClientSand.fade * vi;
        long tick = mc.world.getTime();
        Random rnd = new Random(tick * 31L + 7L); // flickers per tick, not per frame

        if (k > 0.01f) {
            if (cfg.screenFilter) ctx.fill(0, 0, w, h, argb((int) (30 * k), 0, 190, 255));

            if (cfg.vignette) {
                int steps = 18;
                for (int i = 0; i < steps; i++) {
                    float f = 1f - i / (float) steps;
                    int a = (int) (95 * k * f * f);
                    int t = i * 3;
                    ctx.fill(0, t, w, t + 3, argb(a, 0, 20, 40));
                    ctx.fill(0, h - t - 3, w, h - t, argb(a, 0, 20, 40));
                    ctx.fill(t, 0, t + 3, h, argb(a, 0, 20, 40));
                    ctx.fill(w - t - 3, 0, w - t, h, argb(a, 0, 20, 40));
                }
            }

            if (cfg.chromaticAberration) {
                int steps = 22;
                float amount = k * (1f + ClientSand.stress * 1.5f + (ClientSand.dodgeFlash > 0 ? 1.2f : 0f));
                for (int i = 0; i < steps; i++) {
                    float f = 1f - i / (float) steps;
                    int a = (int) (55 * amount * f * f);
                    ctx.fill(i * 2, 0, i * 2 + 2, h, argb(a, 255, 40, 70));          // red fringe, left
                    ctx.fill(w - i * 2 - 2, 0, w - i * 2, h, argb(a, 40, 220, 255)); // cyan fringe, right
                }
            }

            if (cfg.speedLines) {
                double speed = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
                int n = (int) (Math.min(1.0, speed * 2.2) * 34 * k);
                for (int i = 0; i < n; i++) {
                    boolean left = rnd.nextBoolean();
                    int y = rnd.nextInt(h);
                    int len = 24 + rnd.nextInt(80);
                    int edge = rnd.nextInt(w / 4);
                    int x = left ? edge : w - edge - len;
                    ctx.fill(x, y, x + len, y + 1, argb(70 + rnd.nextInt(60), 190, 245, 255));
                }
            }

            if (cfg.glitchEffects && ClientSand.stress > 0.45f) {
                float g = (ClientSand.stress - 0.45f) / 0.55f;
                int bars = (int) (g * 5);
                for (int i = 0; i < bars; i++) {
                    if (rnd.nextFloat() > 0.45f) continue;
                    int y = rnd.nextInt(h);
                    int bh = 1 + rnd.nextInt(5 + (int) (g * 8));
                    int shift = rnd.nextInt(60) - 30;
                    ctx.fill(Math.max(0, shift), y, w, y + bh, argb((int) (40 + 70 * g), rnd.nextBoolean() ? 255 : 0, 40, 255));
                }
                if (g > 0.6f && rnd.nextFloat() < 0.12f) ctx.fill(0, 0, w, h, argb((int) (40 * g), 255, 255, 255));
            }
        }

        // activation / dash pulses
        if (ClientSand.flash > 0 && cfg.chromaticAberration) {
            float f = ClientSand.flash / 16f;
            ctx.fill(0, 0, w, h, argb((int) (95 * f * vi), 120, 235, 255));
            int off = (int) (14 * f * vi);
            ctx.fill(0, 0, off, h, argb((int) (120 * f), 255, 40, 70));
            ctx.fill(w - off, 0, w, h, argb((int) (120 * f), 40, 220, 255));
        }
        if (ClientSand.dodgeFlash > 0) {
            float f = ClientSand.dodgeFlash / 9f;
            int off = (int) (22 * f * vi);
            ctx.fill(0, 0, off, h, argb((int) (110 * f), 255, 40, 70));
            ctx.fill(w - off, 0, w, h, argb((int) (110 * f), 40, 220, 255));
        }
        if (ClientSand.overloadFx > 0) { // CYBERPSYCHOSIS
            float f = ClientSand.overloadFx / 50f;
            ctx.fill(0, 0, w, h, argb((int) (110 * f), 255, 20, 40));
            for (int i = 0; i < 14; i++) {
                int y = rnd.nextInt(h);
                ctx.fill(0, y, w, y + 1 + rnd.nextInt(7), argb((int) (150 * f), 255, 255, 255));
            }
        }

        drawHud(ctx, mc, tick);
    }

    private static void bar(DrawContext ctx, int x, int y, int w, float v, int color, int back) {
        ctx.fill(x, y, x + w, y + 3, back);
        ctx.fill(x, y, x + (int) (w * MathHelper.clamp(v, 0f, 1f)), y + 3, color);
    }

    private static void drawHud(DrawContext ctx, MinecraftClient mc, long tick) {
        int x = 10, y = 10, w = 116;
        boolean over = ClientSand.overload > 0;
        boolean blink = (tick / 4) % 2 == 0;
        int accent = over ? 0xFFFF2A3A : ClientSand.active ? 0xFF19D6FF : 0xFF6E8A94;

        ctx.fill(x - 4, y - 3, x + w + 4, y + 56, 0x7A000A12);
        ctx.fill(x - 4, y - 3, x - 2, y + 56, accent);

        ctx.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.sandevistan.title"), x + 2, y, accent);
        ctx.drawTextWithShadow(mc.textRenderer, Text.translatable("tier.sandevistan." + tierKey(ClientSand.tier)), x + 2, y + 10, 0xFF9FB6BE);

        Text state;
        int stateColor;
        if (over) {
            state = Text.translatable("hud.sandevistan.overload");
            stateColor = blink ? 0xFFFF2A3A : 0xFF8A1020;
        } else if (ClientSand.active) {
            state = Text.translatable("hud.sandevistan.online", ClientSand.level);
            stateColor = 0xFF19D6FF;
        } else if (ClientSand.cooldown > 0) {
            state = Text.translatable("hud.sandevistan.cooling", ClientSand.cooldown / 20 + 1);
            stateColor = 0xFFFFC107;
        } else {
            state = Text.translatable("hud.sandevistan.ready", Math.max(1, ClientSand.level));
            stateColor = 0xFF7CFFB2;
        }
        ctx.drawTextWithShadow(mc.textRenderer, state, x + 2, y + 20, stateColor);

        bar(ctx, x + 2, y + 32, w - 4, ClientSand.energy, 0xFF19D6FF, 0xFF0E2A33);
        float s = ClientSand.stress;
        int stressColor = s < 0.5f ? 0xFFFFD54A : s < 0.8f ? 0xFFFF8A2A : 0xFFFF2A3A;
        bar(ctx, x + 2, y + 38, w - 4, s, stressColor, 0xFF2A1A0E);

        if (ClientSand.active) {
            int secs = ClientSand.remaining / 20;
            ctx.drawTextWithShadow(mc.textRenderer, Text.translatable("hud.sandevistan.time", secs / 60, secs % 60), x + 2, y + 44, 0xFF9FB6BE);
        }
    }

    private static String tierKey(int tier) {
        return switch (tier) {
            case 1 -> "experimental";
            case 2 -> "military";
            case 3 -> "arasaka";
            default -> "none";
        };
    }
}
