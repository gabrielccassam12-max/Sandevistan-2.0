package com.sandevistan.client;

import com.sandevistan.net.DilationPayload;
import com.sandevistan.time.WorldDilation;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/** Client mirror of the server's time dilation (see WorldDilation). */
public final class ClientDilation {
    private ClientDilation() {}

    public static final WorldDilation STATE = new WorldDilation();
    private static Entity pendingEntity;
    private static Vec3d pendingVelocity;

    public static void receive(DilationPayload p) {
        STATE.target = p.target();
        STATE.mode = p.mode();
        STATE.owners.clear();
        STATE.owners.addAll(p.owners());
        STATE.selected.clear();
        STATE.excluded.clear();
        for (String s : p.filter()) {
            if (s.startsWith("!")) STATE.excluded.add(s.substring(1));
            else STATE.selected.add(s);
        }
    }

    public static void tick() {
        STATE.tickRamp();
    }

    public static void reset() {
        STATE.reset();
        pendingEntity = null;
    }

    public static boolean running() {
        return STATE.running();
    }

    /** 1 = normal; lower while time is slowed, so sounds drop in pitch. */
    public static float soundFactor() {
        if (STATE.current >= 0.999f) return 1f;
        float strength = (float) com.sandevistan.config.SandConfig.INSTANCE.soundIntensity;
        float slow = 1f - STATE.current;              // 0..1
        return Math.max(0.35f, 1f - 0.55f * slow * strength);
    }

    public static int decide(Entity e) {
        if (STATE.current >= 0.999f) return WorldDilation.RUN;
        int r = STATE.decide(e);
        if (r == WorldDilation.SCALE) {
            pendingEntity = e;
            pendingVelocity = STATE.scaleBefore(e);
        }
        return r;
    }

    public static void afterTick(Entity e) {
        if (pendingEntity == e) {
            STATE.scaleAfter(e, pendingVelocity);
            pendingEntity = null;
        }
    }
}
