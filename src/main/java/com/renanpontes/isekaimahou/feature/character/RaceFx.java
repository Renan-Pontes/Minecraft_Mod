package com.renanpontes.isekaimahou.feature.character;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Partículas e sons das skills raciais (servidor). Tudo com orçamento: nada aqui roda por tick sem limite. */
public final class RaceFx {
    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F),
                Math.max(0.2F, Math.min(4F, size)));
    }

    public static void send(ServerLevel level, ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    public static void dustBurst(ServerLevel level, Vec3 at, int rgb, int count, double spread) {
        level.sendParticles(dust(rgb, 1.3F), at.x, at.y, at.z, count, spread, spread, spread, 0.02);
    }

    /** Anel horizontal de partículas. */
    public static void ring(ServerLevel level, Vec3 center, double radius, int points, ParticleOptions p) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Espiral subindo em volta de uma entidade (aura de buff). */
    public static void spiral(ServerLevel level, Entity e, ParticleOptions p, int points, double radius) {
        double h = e.getBbHeight();
        for (int i = 0; i < points; i++) {
            double t = i / (double) points;
            double a = t * Math.PI * 4 + e.tickCount * 0.3;
            level.sendParticles(p, e.getX() + Math.cos(a) * radius, e.getY() + t * h * 1.1, e.getZ() + Math.sin(a) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    /** Linha de partículas entre dois pontos (passo ~0,35 bloco, no máx. 48 pontos). */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p) {
        Vec3 d = to.subtract(from);
        int n = (int) Math.min(48, Math.max(2, d.length() / 0.35));
        for (int i = 0; i <= n; i++) {
            Vec3 at = from.add(d.scale(i / (double) n));
            level.sendParticles(p, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** Mostra/tira o visual temporário ({@link CharacterNetwork#FX_WINGS}...) para quem vê a entidade e para ela. */
    public static void entityFx(LivingEntity e, int kind, int ticks) {
        if (e.level().isClientSide()) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new CharacterNetwork.RaceFx(e.getId(), kind, ticks));
    }

    private RaceFx() {}
}
