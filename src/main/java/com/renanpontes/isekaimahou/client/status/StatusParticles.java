package com.renanpontes.isekaimahou.client.status;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

/**
 * Partículas de cada condição, geradas no próprio cliente (sem tráfego de rede): gotas no Molhado, brasas no Em
 * Chamas, neve no Gelado/Congelado, faíscas no Eletrizado, gotas de sangue, poeira de pedra, estrelinhas no Atordoado.
 */
final class StatusParticles {
    private static final double RANGE = 32.0;
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55F, 0.03F, 0.06F), 0.9F);
    private static final DustParticleOptions FROST = new DustParticleOptions(new Vector3f(0.70F, 0.90F, 1.00F), 0.8F);
    private static final DustParticleOptions STONE_DUST = new DustParticleOptions(new Vector3f(0.55F, 0.53F, 0.50F), 1.0F);
    private static final DustParticleOptions CORRUPT = new DustParticleOptions(new Vector3f(0.40F, 0.12F, 0.55F), 1.0F);
    private static final DustParticleOptions STARS = new DustParticleOptions(new Vector3f(1.0F, 0.92F, 0.35F), 0.7F);
    private static BlockParticleOption stoneFall;

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused() || ClientStatuses.trackedIds().isEmpty()) return;
        float rate = reduced() ? 0.45F : 1.0F;
        RandomSource r = level.random;
        List<Integer> ids = new ArrayList<>(ClientStatuses.trackedIds());
        for (int id : ids) {
            Entity ent = level.getEntity(id);
            if (!(ent instanceof LivingEntity le) || le.isRemoved() || le.isInvisible()) continue;
            if (le.distanceToSqr(mc.player) > RANGE * RANGE) continue;
            // O próprio jogador em primeira pessoa: só poucas partículas (não tapar a visão)
            float own = le == mc.player && mc.options.getCameraType().isFirstPerson() ? 0.25F : 1.0F;
            for (ClientStatuses.Entry en : ClientStatuses.get(le)) {
                float k = rate * own;
                switch (en.kind()) {
                    case WET -> {
                        if (!le.isInWater()) spawn(level, le, r, ParticleTypes.FALLING_WATER, 0.30F * k, 0.6, 0);
                    }
                    case BURNING -> {
                        spawn(level, le, r, ParticleTypes.SMALL_FLAME, (0.15F + 0.08F * en.stacks()) * k, 0.5, 0.01);
                        spawn(level, le, r, ParticleTypes.SMOKE, 0.06F * en.stacks() * k, 0.9, 0.02);
                        if (en.stacks() >= 3) spawn(level, le, r, ParticleTypes.LAVA, 0.02F * k, 0.4, 0);
                    }
                    case CHILLED -> {
                        spawn(level, le, r, ParticleTypes.SNOWFLAKE, 0.08F * en.stacks() * k, 0.5, 0.0);
                        spawn(level, le, r, FROST, 0.05F * en.stacks() * k, 0.4, 0);
                    }
                    case FROZEN -> spawn(level, le, r, ParticleTypes.SNOWFLAKE, 0.35F * k, 0.6, 0.0);
                    case SHOCKED -> spawn(level, le, r, ParticleTypes.ELECTRIC_SPARK, 0.18F * en.stacks() * k, 0.5, 0.08);
                    case BLEEDING -> spawn(level, le, r, BLOOD, (0.10F + 0.04F * Math.min(en.stacks(), 6)) * k, 0.45, 0);
                    case PETRIFIED -> {
                        if (en.stacks() >= 3) spawn(level, le, r, stoneFall(), 0.25F * k, 0.6, 0);
                        else spawn(level, le, r, STONE_DUST, 0.08F * en.stacks() * k, 0.5, 0);
                    }
                    case CORRUPTED -> spawn(level, le, r, CORRUPT, 0.15F * k, 0.5, 0);
                    case STUNNED -> stunStars(level, le, k);
                    default -> { }
                }
            }
        }
    }

    private static BlockParticleOption stoneFall() {
        if (stoneFall == null) stoneFall = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.STONE.defaultBlockState());
        return stoneFall;
    }

    private static void spawn(ClientLevel level, LivingEntity le, RandomSource r, ParticleOptions p, float chance, double spread, double up) {
        int n = (int) chance;
        if (r.nextFloat() < chance - n) n++;
        for (int i = 0; i < n; i++) {
            double x = le.getX() + (r.nextDouble() - 0.5) * le.getBbWidth() * 2 * spread;
            double y = le.getY() + r.nextDouble() * le.getBbHeight();
            double z = le.getZ() + (r.nextDouble() - 0.5) * le.getBbWidth() * 2 * spread;
            level.addParticle(p, x, y, z, 0, up, 0);
        }
    }

    /** Estrelinhas girando em volta da cabeça. */
    private static void stunStars(ClientLevel level, LivingEntity le, float k) {
        if (level.random.nextFloat() > 0.6F * k) return;
        float t = (le.tickCount % 40) / 40F * Mth.TWO_PI;
        double rr = Math.max(0.35, le.getBbWidth() * 0.6);
        for (int i = 0; i < 3; i++) {
            double a = t + i * (Mth.TWO_PI / 3);
            level.addParticle(STARS, le.getX() + Math.cos(a) * rr, le.getY() + le.getBbHeight() + 0.25, le.getZ() + Math.sin(a) * rr, 0, 0, 0);
        }
    }

    private static boolean reduced() {
        try {
            return MahouConfig.REDUCED_PARTICLES.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private StatusParticles() {}
}
