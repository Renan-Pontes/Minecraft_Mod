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

    /** Círculo de runas no chão (anel + runas que brilham) — desenhado no cliente (KnightFxClient). */
    public static void runeCircle(ServerLevel level, Vec3 c, double r, int rgb, int points, float spin) {
        KnightFxNet.at(level, KnightFxNet.RUNE_CIRCLE, c, Vec3.ZERO, (float) r, rgb, -1);
    }

    /** Meia-lua de lâmina: arco curvado para trás, de largura {@code half} para cada lado (desenhada no cliente). */
    public static void crescent(ServerLevel level, Vec3 pos, Vec3 dir, double half, int rgb, int n, float size) {
        KnightFxNet.at(level, KnightFxNet.CRESCENT, pos, dir, (float) half, rgb, -1, n >= 9 ? 1 : 0);
    }

    /** Linha de partículas de {@code from} até {@code to} (passo em blocos). */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, int rgb, double step, float size) {
        KnightFxNet.at(level, KnightFxNet.LINE, from, to, size / 1.6F, rgb, -1);
    }

    /** Coluna de luz subindo de {@code base}. */
    public static void pillar(ServerLevel level, Vec3 base, double height, double r, int rgb) {
        KnightFxNet.at(level, KnightFxNet.PILLAR, base, Vec3.ZERO, (float) r, rgb, -1, (int) Math.round(height));
    }

    private KnightFx() {}
}
