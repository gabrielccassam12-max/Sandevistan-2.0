package com.sandevistan.implant;

import net.minecraft.util.math.Direction;

/** Runtime (not saved) state of one player's implant, held by the server. */
public class SandState {
    public boolean active;
    public int level = 1;
    public float energy;
    public float stress;
    public int cooldown;
    public int overload;
    public int activeTicks;
    public int riftTicks;
    public int dodgeLock;
    public boolean rechargedNotified = true;
    public final int[] skillCd = new int[Skill.values().length];

    // movement
    public boolean prevOnGround = true;
    public int slideTicks;
    public int slideCd;
    public boolean wallRunning;
    public int wallTicks;
    public int wallCd;
    public Direction wallDir;

    public int hudTimer;
    public double lastSlowApplied = 1.0;
}
