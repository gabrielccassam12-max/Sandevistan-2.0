package com.sandevistan.implant;

import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** Small particle helpers shared by movement and skills. Kept sober and "technological". */
public final class ImplantFx {
    private ImplantFx() {}

    public static final DustParticleEffect CYAN = new DustParticleEffect(new Vector3f(0.1f, 0.85f, 1.0f), 0.9f);
    public static final DustParticleEffect YELLOW = new DustParticleEffect(new Vector3f(1.0f, 0.85f, 0.1f), 0.9f);

    public static void shockRing(ServerWorld w, Vec3d c, double r, ParticleEffect fx) {
        int n = (int) (14 + r * 6);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            w.spawnParticles(fx, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0, 0.02, 0, 0.0);
        }
        w.spawnParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 4, r * 0.4, 0.02, r * 0.4, 0.03);
    }

    /** A luminous line of energy between two points. */
    public static void line(ServerWorld w, Vec3d a, Vec3d b, ParticleEffect fx, double step) {
        Vec3d d = b.subtract(a);
        int n = Math.max(1, (int) (d.length() / step));
        for (int i = 0; i <= n; i++) {
            Vec3d p = a.add(d.multiply(i / (double) n));
            w.spawnParticles(fx, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }
}
