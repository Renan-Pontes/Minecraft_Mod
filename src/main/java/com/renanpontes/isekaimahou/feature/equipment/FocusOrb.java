package com.renanpontes.isekaimahou.feature.equipment;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Posição do Foco Mágico que flutua ao lado do ombro direito de quem o equipa.
 * Usada no servidor (partículas ao equipar/conjurar) e no cliente (renderer e brilho), para que tudo
 * saia do mesmo ponto. Medidas relativas ao corpo, em blocos (já multiplicadas pela escala da entidade).
 */
public final class FocusOrb {
    /** Afastamento para a direita do corpo. */
    public static final float SIDE = 0.62F;
    /** Um pouco para trás do ombro, para não cobrir a mão estendida. */
    public static final float BACK = 0.12F;
    /** Altura acima dos pés. */
    public static final float HEIGHT = 1.62F;
    /** Amplitude e velocidade da flutuação. */
    public static final float BOB = 0.06F;
    public static final float BOB_SPEED = 0.1F;
    /** Quanto o modelo do jogador desce ao agachar (mesmo valor do renderer vanilla). */
    public static final float CROUCH_DROP = 0.125F;

    /** Deslocamento vertical da flutuação num instante (ticks + partialTick). */
    public static float bob(float time) {
        return Mth.sin(time * BOB_SPEED) * BOB;
    }

    public static Vec3 position(LivingEntity e, float partialTick) {
        float yaw = Mth.lerp(partialTick, e.yBodyRotO, e.yBodyRot) * Mth.DEG_TO_RAD;
        // Frente do corpo = (-sin, cos); direita = (-cos, -sin)
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        double s = e.getScale();
        double y = HEIGHT + bob(e.tickCount + partialTick) - (e.isCrouching() ? CROUCH_DROP : 0.0);
        double x = e.getX() + (rx * SIDE - fx * BACK) * s;
        double z = e.getZ() + (rz * SIDE - fz * BACK) * s;
        return new Vec3(x, e.getY() + y * s, z);
    }

    private FocusOrb() {}
}
