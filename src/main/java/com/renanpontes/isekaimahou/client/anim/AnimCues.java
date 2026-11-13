package com.renanpontes.isekaimahou.client.anim;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.core.anim.AnimConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Impacto das animações, todo no cliente (cada um vê o efeito do jogador animado sem pacote extra): anel de poeira do
 * bloco do chão, lufada, faísca do saque e o som curto. Quantidades por {@link FxQuality#count}, extras só em
 * {@link Graphics#HIGH}+, e nada além de {@link FxQuality#range()} da câmera.
 */
final class AnimCues {
    static void fire(AbstractClientPlayer p, AnimLibrary.Cue cue) {
        if (cue == AnimLibrary.Cue.NONE || !AnimConfig.ANIM_CUES.get()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double range = FxQuality.range();
        if (p.position().distanceToSqr(cam) > range * range) return;
        boolean self = p == mc.player;
        switch (cue) {
            case SLAM -> {
                ring(level, p, FxQuality.count(22), 1.6, 0.28);
                puff(level, p, FxQuality.count(6), 0.6);
                if (FxQuality.atLeast(Graphics.HIGH)) {
                    level.addParticle(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.2, p.getZ(), 0, 0, 0);
                    ring(level, p, FxQuality.count(14), 2.6, 0.18);
                }
            }
            case LANDING -> {
                ring(level, p, FxQuality.count(18), 1.3, 0.24);
                puff(level, p, FxQuality.count(5), 0.5);
                if (FxQuality.atLeast(Graphics.HIGH)) ring(level, p, FxQuality.count(10), 2.2, 0.14);
                sound(level, p, SoundEvents.GENERIC_BIG_FALL, 0.7F, 0.6F, self);
            }
            case THUD -> {
                ring(level, p, FxQuality.count(10), 0.9, 0.12);
                puff(level, p, FxQuality.count(4), 0.7);
                sound(level, p, SoundEvents.PLAYER_BIG_FALL, 0.8F, 0.75F, self);
            }
            case ROLL -> puff(level, p, FxQuality.count(5), 0.4);
            case DRAW -> {
                spark(level, p, FxQuality.count(4));
                sound(level, p, SoundEvents.ARMOR_EQUIP_IRON.value(), 0.6F, 1.6F, self);
            }
            case DRAW_HEAVY -> sound(level, p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.7F, 0.8F, self);
            case VICTORY -> {
                RandomSource r = level.random;
                int n = FxQuality.count(10);
                for (int i = 0; i < n; i++) {
                    level.addParticle(ParticleTypes.FIREWORK, p.getX() + (r.nextDouble() - 0.5) * 0.6, p.getY() + 2.3,
                            p.getZ() + (r.nextDouble() - 0.5) * 0.6, (r.nextDouble() - 0.5) * 0.15, 0.12 + r.nextDouble() * 0.1,
                            (r.nextDouble() - 0.5) * 0.15);
                }
                if (FxQuality.atLeast(Graphics.HIGH)) {
                    for (int i = 0; i < FxQuality.count(6); i++) {
                        level.addParticle(ParticleTypes.END_ROD, p.getX() + (r.nextDouble() - 0.5), p.getY() + 0.2 + r.nextDouble() * 1.6,
                                p.getZ() + (r.nextDouble() - 0.5), 0, 0.03, 0);
                    }
                }
            }
            default -> { }
        }
    }

    /** Anel de poeira do bloco embaixo do jogador (ou nuvem, se for ar/fluido). */
    private static void ring(ClientLevel level, AbstractClientPlayer p, int n, double radius, double speed) {
        BlockState below = level.getBlockState(BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ()));
        ParticleOptions dust = below.isAir() || below.getRenderShape() == RenderShape.INVISIBLE || !below.getFluidState().isEmpty()
                ? ParticleTypes.CLOUD : new BlockParticleOption(ParticleTypes.BLOCK, below);
        RandomSource r = level.random;
        for (int i = 0; i < n; i++) {
            double a = (i + r.nextDouble() * 0.5) * Mth.TWO_PI / Math.max(1, n);
            double cx = Math.cos(a), cz = Math.sin(a);
            double d = radius * (0.35 + r.nextDouble() * 0.25);
            level.addParticle(dust, p.getX() + cx * d, p.getY() + 0.05, p.getZ() + cz * d, cx * speed, 0.05 + r.nextDouble() * 0.08,
                    cz * speed);
        }
    }

    private static void puff(ClientLevel level, AbstractClientPlayer p, int n, double spread) {
        RandomSource r = level.random;
        for (int i = 0; i < n; i++) {
            level.addParticle(ParticleTypes.POOF, p.getX() + (r.nextDouble() - 0.5) * spread, p.getY() + 0.1,
                    p.getZ() + (r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * 0.06, 0.02, (r.nextDouble() - 0.5) * 0.06);
        }
    }

    /** Faíscas na altura da mão, à frente do jogador. */
    private static void spark(ClientLevel level, AbstractClientPlayer p, int n) {
        Vec3 look = Vec3.directionFromRotation(0, p.yBodyRot);
        Vec3 at = p.position().add(look.scale(0.5)).add(0, 1.3, 0);
        RandomSource r = level.random;
        for (int i = 0; i < n; i++) {
            level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, (r.nextDouble() - 0.5) * 0.3, r.nextDouble() * 0.2,
                    (r.nextDouble() - 0.5) * 0.3);
        }
    }

    private static void sound(ClientLevel level, AbstractClientPlayer p, SoundEvent s, float vol, float pitch, boolean self) {
        level.playLocalSound(p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, self ? vol : vol * 0.8F,
                pitch + (level.random.nextFloat() - 0.5F) * 0.1F, false);
    }

    private AnimCues() {}
}
