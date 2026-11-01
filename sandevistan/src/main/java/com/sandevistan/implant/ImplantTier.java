package com.sandevistan.implant;

import com.sandevistan.config.SandConfig;
import net.minecraft.text.Text;

/** The three implant models. Each has its own power, endurance, risk and unlocked skills. */
public enum ImplantTier {
    //   id key            maxLvl energy drain stress  worldMul playerMul
    NONE(0, "none",         1,     0,   1.0f, 0.0f,    1.0f,   1.0f),
    EXPERIMENTAL(1, "experimental", 2, 500, 1.0f, 0.0012f, 1.3f, 0.8f),
    MILITARY(2, "military", 3,   800,  1.0f, 0.0015f,  1.0f,   1.0f),
    ARASAKA(3, "arasaka",   3,   700,  1.2f, 0.0028f,  0.6f,   1.3f);

    public static final float[] LEVEL_DRAIN = {1.0f, 1.4f, 2.2f};
    public static final float[] LEVEL_STRESS = {1.0f, 1.7f, 3.2f};

    public final int id;
    public final String key;
    public final int maxLevel;
    public final float energy;
    public final float drain;
    public final float stress;
    public final float worldMul;
    public final float playerMul;

    ImplantTier(int id, String key, int maxLevel, float energy, float drain, float stress, float worldMul, float playerMul) {
        this.id = id;
        this.key = key;
        this.maxLevel = maxLevel;
        this.energy = energy;
        this.drain = drain;
        this.stress = stress;
        this.worldMul = worldMul;
        this.playerMul = playerMul;
    }

    public static ImplantTier of(int id) {
        for (ImplantTier t : values()) {
            if (t.id == id) return t;
        }
        return NONE;
    }

    public Text displayName() {
        return Text.translatable("tier.sandevistan." + key);
    }

    /** World speed at a level (1-3) for this model. */
    public float worldFactor(int level) {
        double f = SandConfig.INSTANCE.worldSpeed[Math.max(0, Math.min(2, level - 1))] * worldMul;
        return (float) Math.max(0.005, Math.min(1.0, f));
    }

    /** Owner speed multiplier at a level (1-3) for this model. */
    public float playerMultiplier(int level) {
        double m = SandConfig.INSTANCE.playerSpeed[Math.max(0, Math.min(2, level - 1))] * playerMul;
        return (float) Math.max(1.0, m);
    }
}
