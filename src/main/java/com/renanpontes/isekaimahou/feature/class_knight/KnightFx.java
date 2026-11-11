package com.renanpontes.isekaimahou.feature.class_knight;

import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Visual do Cavaleiro Rúnico (servidor → partículas): círculos de runas, meias-luas de lâmina, feixes e brasões. */
public final class KnightFx {
    public static final int GOLD = 0xFFD86B;
    public static final int RUNE_BLUE = 0x8FD8FF;
    public static final int STEEL = 0xC8D2E6;

    public static ParticleOptions dust(int rgb, float size) {
        return WeaponFx.dust(rgb, size);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
        WeaponFx.sound(level, at, s, vol, pitch);
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() / 2, 0);
    }

    /** Direção horizontal (sem y), com reserva se for vertical. */
    public static Vec3 flat(Vec3 dir, Vec3 fallback) {
        Vec3 f = new Vec3(dir.x, 0, dir.z);
        if (f.lengthSqr() < 1.0E-4) f = new Vec3(fallback.x, 0, fallback.z);
        if (f.lengthSqr() < 1.0E-4) f = new Vec3(0, 0, 1);
        return f.normalize();
    }

    /** Eixo perpendicular (horizontal) a uma direção qualquer. */
    public static Vec3 side(Vec3 dir) {
        Vec3 s = dir.cross(new Vec3(0, 1, 0));
        if (s.lengthSqr() < 1.0E-4) s = new Vec3(1, 0, 0);
        return s.normalize();
    }

    /** Círculo de runas no chão (anel + 6 runas que brilham alternadas). */
    public static void runeCircle(ServerLevel level, Vec3 c, double r, int rgb, int points, float spin) {
        ParticleOptions d = dust(rgb, 0.9F);
        for (int i = 0; i < points; i++) {
            double a = spin + Mth.TWO_PI * i / points;
            level.sendParticles(d, c.x + Math.cos(a) * r, c.y + 0.05, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 6; i++) {
            double a = -spin * 1.5 + Mth.TWO_PI * i / 6;
            double rr = r * 0.62;
            level.sendParticles(i % 2 == 0 ? ParticleTypes.ENCHANT : dust(GOLD, 1.1F), c.x + Math.cos(a) * rr, c.y + 0.1,
                    c.z + Math.sin(a) * rr, 2, 0.05, 0.02, 0.05, 0);
        }
    }

    /** Meia-lua de lâmina: arco curvado para trás, de largura {@code half} para cada lado. */
    public static void crescent(ServerLevel level, Vec3 pos, Vec3 dir, double half, int rgb, int n, float size) {
        Vec3 s = side(dir);
        Vec3 up = s.cross(dir).normalize();
        ParticleOptions core = dust(rgb, size);
        ParticleOptions rim = dust(0xFFFFFF, size * 0.6F);
        for (int k = -n; k <= n; k++) {
            double t = k / (double) n;
            Vec3 p = pos.add(s.scale(t * half)).subtract(dir.scale(t * t * half * 0.55)).add(up.scale(t * 0.15 * half));
            level.sendParticles(core, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            if ((k & 1) == 0) {
                Vec3 q = p.add(dir.scale(0.18));
                level.sendParticles(rim, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Linha de partículas de {@code from} até {@code to} (passo em blocos). */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, int rgb, double step, float size) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1.0E-3) return;
        Vec3 u = d.scale(1 / len);
        ParticleOptions p = dust(rgb, size);
        int n = Math.min(160, (int) (len / step));
        for (int i = 0; i <= n; i++) {
            Vec3 q = from.add(u.scale(i * step));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0);
        }
    }

    /** Coluna de luz subindo de {@code base}. */
    public static void pillar(ServerLevel level, Vec3 base, double height, double r, int rgb) {
        ParticleOptions p = dust(rgb, 1.4F);
        for (int i = 0; i < 24; i++) {
            double a = i * 0.9;
            double y = height * i / 24.0;
            level.sendParticles(p, base.x + Math.cos(a) * r, base.y + y, base.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.END_ROD, base.x, base.y + height * 0.5, base.z, 10, r * 0.4, height * 0.4, r * 0.4, 0.02);
    }

    private KnightFx() {}
}
