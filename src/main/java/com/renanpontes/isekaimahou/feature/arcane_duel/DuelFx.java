package com.renanpontes.isekaimahou.feature.arcane_duel;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.arcane_duel.entity.DuelFxEntity;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Visual e som do lado do servidor: cria as entidades de visual e solta partículas/sons com orçamento. */
public final class DuelFx {
    public static final int ARCANE = 0xB98CFF;
    public static final int GLASS = 0xD8E8FF;
    public static final int MANA = 0x5FA8FF;
    public static final int GOLD = 0xFFD24A;
    public static final int HEX = 0x7FE3FF;
    public static final int VOID = 0x8A3CFF;
    public static final int DEATH = 0x58E06A;
    public static final int HOLY = 0xFFF1B8;
    public static final int TIME = 0x9FD8FF;

    /** Teto de visuais por chamada de área (Ira Divina, Redirecionar...). */
    public static final int MAX_FX_PER_CAST = 24;

    /** Teto de visuais soltos criados por tick (uma área que mata 60 mobs com Toque Mortal não cria 60 entidades). */
    public static final int MAX_SPAWNS_PER_TICK = 32;
    private static long budgetTick = Long.MIN_VALUE;
    private static int budgetUsed;

    /** Cria um visual solto no mundo. @return null se o orçamento deste tick acabou (as partículas/sons seguem). */
    @Nullable
    public static DuelFxEntity spawn(ServerLevel level, int kind, Vec3 pos, float radius, int life, int rgb, float param) {
        long now = level.getServer().getTickCount();
        if (now != budgetTick) {
            budgetTick = now;
            budgetUsed = 0;
        }
        if (++budgetUsed > MAX_SPAWNS_PER_TICK) return null;
        DuelFxEntity fx = DuelFxEntity.create(level, kind, pos.x, pos.y, pos.z, radius, life, rgb, param, null);
        level.addFreshEntity(fx);
        return fx;
    }

    /**
     * Cria (ou estende, se já existir) um visual preso a {@code target}. Reaplicar Resguardo/Indestrutível não empilha
     * cascas: a que existe ganha vida nova.
     */
    public static DuelFxEntity attach(ServerLevel level, int kind, Entity target, float radius, int life, int rgb, float param) {
        for (DuelFxEntity old : level.getEntitiesOfClass(DuelFxEntity.class, target.getBoundingBox().inflate(1.5),
                f -> f.kind() == kind && f.attachId() == target.getId())) {
            old.extendLife(life);
            return old;
        }
        DuelFxEntity fx = DuelFxEntity.create(level, kind, target.getX(), target.getY(), target.getZ(), radius, life, rgb, param, target);
        level.addFreshEntity(fx);
        return fx;
    }

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
    }

    /** Anel horizontal de partículas. */
    public static void ring(ServerLevel level, Vec3 c, double r, ParticleOptions p, int count) {
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            level.sendParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    /** Linha de partículas (passo {@code step} blocos, no máx. 64 pontos). */
    public static void line(ServerLevel level, Vec3 a, Vec3 b, ParticleOptions p, double step) {
        Vec3 d = b.subtract(a);
        int n = (int) Math.min(64, Math.max(1, d.length() / step));
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(d.scale(i / (double) n));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    /** Cacos de vidro mágico voando (partículas de bloco de vidro tingido) + brilho. */
    public static void glassBurst(ServerLevel level, Vec3 c, double spread, int count) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PURPLE_STAINED_GLASS.defaultBlockState()),
                c.x, c.y, c.z, count, spread, spread, spread, 0.25);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState()),
                c.x, c.y, c.z, count / 2, spread, spread, spread, 0.25);
        level.sendParticles(Vfx.magicCrit(), c.x, c.y, c.z, count / 2 + 4, spread, spread, spread, 0.4);
    }

    /** Vidro mágico estilhaçando com som (o visual principal das contramágicas do módulo). */
    public static void shatter(ServerLevel level, Vec3 c, float radius, int rgb, @Nullable Entity facing) {
        spawn(level, DuelFxEntity.SHATTER, c, radius, 22, rgb, facing != null ? facing.getYRot() : 0F);
        glassBurst(level, c, radius * 0.4, 18 + (int) (radius * 6));
        sound(level, c, net.minecraft.sounds.SoundEvents.GLASS_BREAK, 1.0F, 0.75F);
        sound(level, c, net.minecraft.sounds.SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 1.3F);
    }

    public static void clear() {
        budgetTick = Long.MIN_VALUE;
        budgetUsed = 0;
    }

    private DuelFx() {}
}
