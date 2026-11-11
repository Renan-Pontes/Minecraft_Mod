package com.renanpontes.isekaimahou.feature.class_ranger;

import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Visual do módulo (partículas e sons enviados pelo servidor), sempre com teto de partículas por chamada. */
public final class RangerFx {
    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static void ring(ServerLevel level, Vec3 c, double r, int points, ParticleOptions p, double speed) {
        points = Math.min(points, 64);
        for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2 * i) / points;
            level.sendParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, speed);
        }
    }

    /** Linha de partículas de {@code from} a {@code to}, uma a cada {@code step} blocos (máx. 64). */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, double step, ParticleOptions p) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-3) return;
        int n = Math.min(64, Math.max(1, (int) (len / step)));
        for (int i = 0; i <= n; i++) {
            Vec3 q = from.add(d.scale(i / (double) n));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    /** Rastro colorido de flecha espectral. */
    public static void tracer(ServerLevel level, Vec3 from, Vec3 to, int rgb, float size) {
        line(level, from, to, 0.5, WeaponFx.dust(rgb, size));
    }

    /** Mira de caçador girando sobre a cabeça (Marca do Caçador). */
    public static void crosshair(ServerLevel level, Entity e, int rgb, long time) {
        Vec3 c = new Vec3(e.getX(), e.getY() + e.getBbHeight() + 0.55, e.getZ());
        double r = 0.45 + e.getBbWidth() * 0.25;
        float spin = time * 0.15F;
        for (int i = 0; i < 4; i++) {
            double a = spin + i * Math.PI / 2;
            level.sendParticles(WeaponFx.dust(rgb, 0.8F), c.x + Mth.cos((float) a) * r, c.y, c.z + Mth.sin((float) a) * r, 1, 0, 0, 0, 0);
            level.sendParticles(WeaponFx.dust(rgb, 0.55F), c.x + Mth.cos((float) a) * r * 0.5, c.y, c.z + Mth.sin((float) a) * r * 0.5, 1, 0, 0, 0, 0);
        }
    }

    private RangerFx() {}
}
