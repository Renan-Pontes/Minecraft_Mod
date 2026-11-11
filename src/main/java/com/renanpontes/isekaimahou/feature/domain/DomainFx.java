package com.renanpontes.isekaimahou.feature.domain;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Ajudantes visuais do lado servidor para efeitos GRANDES (vistos de longe) e tremor de tela. */
public final class DomainFx {
    /** Até onde as partículas "de longa distância" chegam. */
    public static final double FAR = 160.0;

    /**
     * Partículas enviadas com "longDistance" para quem estiver a até {@link #FAR} blocos
     * (o sendParticles comum só alcança 32 blocos — pouco para um Cataclismo ou um domo de 20 blocos).
     */
    public static void far(ServerLevel level, ParticleOptions type, double x, double y, double z, int count,
                           double dx, double dy, double dz, double speed) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) <= FAR * FAR) {
                level.sendParticles(p, type, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    /** Tremor de tela para quem estiver perto; mais forte quanto mais perto do centro. */
    public static void shake(ServerLevel level, Vec3 pos, double range, float strength, int ticks) {
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(pos);
            if (d > range) continue;
            float s = strength * (float) (1.0 - 0.7 * d / range);
            PacketDistributor.sendToPlayer(p, new DomainNet.Shake(s, ticks));
        }
    }

    private DomainFx() {}
}
