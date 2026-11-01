package com.sandevistan.implant;

/** Active skills. minTier = lowest implant model that has it. */
public enum Skill {
    DASH("dash", 1, 30, 30, 0.01f),
    BARRAGE("barrage", 2, 80, 60, 0.04f),
    SLASH("slash", 2, 90, 70, 0.05f),
    KINETIC("kinetic", 1, 70, 50, 0.03f),
    EXECUTE("execute", 2, 120, 80, 0.06f),
    DODGE("dodge", 1, 35, 25, 0.01f),
    SUPERJUMP("superjump", 1, 40, 30, 0.01f),
    RIFT("rift", 3, 400, 200, 0.15f);

    public final String key;
    public final int minTier;
    public final int cooldown;
    public final float energy;
    public final float stress;

    Skill(String key, int minTier, int cooldown, float energy, float stress) {
        this.key = key;
        this.minTier = minTier;
        this.cooldown = cooldown;
        this.energy = energy;
        this.stress = stress;
    }
}
