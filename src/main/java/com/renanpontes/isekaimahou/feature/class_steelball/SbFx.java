package com.renanpontes.isekaimahou.feature.class_steelball;

import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Visual e som da Rotação (servidor): anéis, espirais e o Retângulo Dourado. Orçamento de partículas sempre fixo. */
public final class SbFx {
    /** Aço (cor da classe). */
    public static final int STEEL = 0xB4C0CC;
    /** Rotação Dourada / Retângulo Dourado. */
    public static final int GOLD = 0xFFD24A;
    /** Unhas giratórias (rosa-violeta). */
    public static final int NAIL = 0xE58AE8;
    /** Rotação medicinal (verde suave). */
    public static final int HEAL = 0x8CF2B4;

    public static ParticleOptions dust(int rgb, float size) {
        return WeaponFx.dust(rgb, size);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Anel horizontal de pó. */
    public static void spinRing(ServerLevel level, Vec3 center, int rgb, double radius, int points) {
        ParticleOptions p = dust(rgb, 0.9F);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Espiral subindo em volta de um ponto (giro que sobe pelo corpo). */
    public static void spiral(ServerLevel level, Vec3 base, int rgb, double radius, double height, int points, float phase) {
        ParticleOptions p = dust(rgb, 1.0F);
        for (int i = 0; i < points; i++) {
            double t = i / (double) points;
            double a = phase + t * Math.PI * 6;
            double r = radius * (1.0 - 0.4 * t);
            level.sendParticles(p, base.x + Math.cos(a) * r, base.y + t * height, base.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    /**
     * Retângulo Dourado: a espiral de ouro (quartos de círculo crescendo pela razão áurea) desenhada de frente para
     * {@code facing}, no plano vertical. Visual da rotação perfeita.
     */
    public static void goldenSpiral(ServerLevel level, Vec3 center, Vec3 facing, double size) {
        Vec3 f = facing.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : facing.normalize();
        Vec3 right = new Vec3(-f.z, 0, f.x);
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(f).normalize();
        ParticleOptions gold = dust(GOLD, 0.8F);
        // r(θ) = a·φ^(2θ/π): cresce φ a cada quarto de volta
        double phi = 1.6180339887;
        double a = size / Math.pow(phi, 4);
        for (int i = 0; i <= 48; i++) {
            double th = i / 48.0 * Math.PI * 2;
            double r = a * Math.pow(phi, 2 * th / Math.PI);
            Vec3 p = center.add(right.scale(Math.cos(th) * r)).add(up.scale(Math.sin(th) * r));
            level.sendParticles(gold, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 3, size * 0.2, size * 0.2, size * 0.2, 0.01);
    }

    /** Faíscas de aço no impacto. */
    public static void impact(ServerLevel level, Vec3 at, boolean golden) {
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.25);
        level.sendParticles(dust(golden ? GOLD : STEEL, 1.1F), at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0);
        if (golden) level.sendParticles(ParticleTypes.WAX_OFF, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.3);
    }

    /** Ângulo em graus normalizado (usado para girar mobs). */
    public static float wrap(float deg) {
        return Mth.wrapDegrees(deg);
    }

    private SbFx() {}
}
