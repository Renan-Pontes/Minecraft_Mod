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
        // desenhado no cliente (SbFxClient), respeitando o preset gráfico
        SbFxNet.at(level, SbFxNet.RING, center, Vec3.ZERO, (float) radius, rgb, -1);
    }

    /** Espiral subindo em volta de um ponto (giro que sobe pelo corpo). */
    public static void spiral(ServerLevel level, Vec3 base, int rgb, double radius, double height, int points, float phase) {
        SbFxNet.at(level, SbFxNet.SPIRAL, base, new Vec3(0, height, 0), (float) radius, rgb, -1);
    }

    /**
     * Retângulo Dourado: a espiral de ouro (quartos de círculo crescendo pela razão áurea) desenhada de frente para
     * {@code facing}, no plano vertical. Visual da rotação perfeita.
     */
    public static void goldenSpiral(ServerLevel level, Vec3 center, Vec3 facing, double size) {
        SbFxNet.at(level, SbFxNet.GOLDEN_RECT, center, facing, (float) size, GOLD, -1);
    }

    /** Faíscas de aço no impacto. */
    public static void impact(ServerLevel level, Vec3 at, boolean golden) {
        SbFxNet.at(level, SbFxNet.BALL_HIT, at, Vec3.ZERO, 1F, golden ? GOLD : STEEL, -1);
    }

    /** Ângulo em graus normalizado (usado para girar mobs). */
    public static float wrap(float deg) {
        return Mth.wrapDegrees(deg);
    }

    private SbFx() {}
}
