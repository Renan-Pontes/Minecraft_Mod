package com.renanpontes.isekaimahou.feature.bosses_f.entity;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Partículas e efeitos reaproveitados pelos chefes do módulo. */
public final class BfFx {

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    /** Anel horizontal. */
    public static void ring(ServerLevel level, Vec3 c, double radius, int points, ParticleOptions p, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0.02, 0.02, 0.02, speed);
        }
    }

    /** Linha de partículas entre dois pontos (densidade = pontos por bloco). */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double density) {
        Vec3 d = to.subtract(from);
        int steps = Math.min(160, Math.max(2, (int) (d.length() * density)));
        for (int i = 0; i <= steps; i++) {
            Vec3 at = from.add(d.scale(i / (double) steps));
            level.sendParticles(p, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /** Raio em zigue-zague (partículas) de {@code from} até {@code to}. */
    public static void zigzag(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, int segments, double jitter) {
        Vec3 prev = from;
        for (int i = 1; i <= segments; i++) {
            Vec3 next = from.add(to.subtract(from).scale(i / (double) segments));
            if (i < segments) {
                next = next.add((level.random.nextDouble() - 0.5) * jitter, (level.random.nextDouble() - 0.5) * jitter * 0.5,
                        (level.random.nextDouble() - 0.5) * jitter);
            }
            line(level, prev, next, p, 4.0);
            prev = next;
        }
    }

    /** Bloco do chão (para partículas de poeira/terra); terra se for ar. */
    public static BlockState ground(ServerLevel level, Vec3 at) {
        BlockPos p = BlockPos.containing(at.x, at.y - 0.2, at.z);
        for (int i = 0; i < 4; i++) {
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.getFluidState().isEmpty()) return s;
            p = p.below();
        }
        return Blocks.DIRT.defaultBlockState();
    }

    /** Poeira do chão espalhada num disco. */
    public static void groundBurst(ServerLevel level, Vec3 c, double radius, int count) {
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground(level, c));
        level.sendParticles(dust, c.x, c.y + 0.15, c.z, count, radius * 0.45, 0.15, radius * 0.45, 0.2);
    }

    /**
     * "Cratera" só visual (v1.0: luta de chefe não destrói o cenário): estilhaços do chão saltando, anel de poeira e
     * rachaduras de partícula no raio. Nenhum bloco muda.
     */
    public static void impactCrater(ServerLevel level, Vec3 c, double radius) {
        BlockState g = ground(level, c);
        BlockParticleOption chunks = new BlockParticleOption(ParticleTypes.BLOCK, g);
        int n = (int) Math.min(90, 18 + radius * 18);
        level.sendParticles(chunks, c.x, c.y + 0.3, c.z, n, radius * 0.35, 0.35, radius * 0.35, 0.35);
        ring(level, c.add(0, 0.1, 0), radius, Math.min(36, 10 + (int) (radius * 6)), ParticleTypes.CAMPFIRE_COSY_SMOKE, 0.01);
        // rachaduras radiais (linhas curtas de poeira do próprio chão)
        DustParticleOptions crack = new DustParticleOptions(new Vector3f(0.18F, 0.16F, 0.14F), 1.4F);
        int spokes = 6;
        double off = level.random.nextDouble() * Math.PI;
        for (int i = 0; i < spokes; i++) {
            double a = off + Math.PI * 2 * i / spokes;
            Vec3 tip = c.add(Math.cos(a) * radius * 1.4, 0.08, Math.sin(a) * radius * 1.4);
            line(level, c.add(0, 0.08, 0), tip, crack, 3.0);
        }
    }

    /** Raio só visual (sem fogo nem dano: o dano é do chefe, no golpe telegrafado). */
    public static void visualBolt(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(at.x, at.y, at.z);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    /** Opção "reduzir partículas" do cliente (só chamar no cliente). */
    public static boolean reduced() {
        try {
            return !com.renanpontes.isekaimahou.client.fx.FxQuality.atLeast(MahouConfig.Graphics.HIGH);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private BfFx() {}
}
