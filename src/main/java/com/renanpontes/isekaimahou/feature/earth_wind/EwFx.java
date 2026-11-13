package com.renanpontes.isekaimahou.feature.earth_wind;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Efeitos visuais/sonoros de Terra e Vento (lado servidor, via sendParticles). */
public final class EwFx {

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Estado de bloco usado nas particulas de "terra" naquele ponto (o proprio chao, ou pedra). */
    public static BlockState groundState(ServerLevel level, BlockPos ground) {
        if (ground != null) {
            BlockState s = level.getBlockState(ground);
            if (!s.isAir() && s.getFluidState().isEmpty()) return s;
        }
        return Blocks.STONE.defaultBlockState();
    }

    /** Lascas de bloco espirrando de um ponto. */
    public static void debris(ServerLevel level, Vec3 pos, BlockState state, int count, double spread) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.x, pos.y, pos.z, count, spread, spread, spread, 0.15);
    }

    /** Coluna de poeira saindo do chao (como o impacto da maca). */
    public static void dustPillar(ServerLevel level, Vec3 pos, BlockState state, int count) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, state), pos.x, pos.y, pos.z, count, 0.3, 0.05, 0.3, 0.1);
    }

    /** Anel no chao com particulas do bloco de cada ponto (ondas de tremor). */
    public static void groundRing(ServerLevel level, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points + level.random.nextDouble() * 0.2;
            double x = center.x + Math.cos(a) * radius;
            double z = center.z + Math.sin(a) * radius;
            BlockPos ground = EwUtil.groundBelow(level, BlockPos.containing(x, center.y + 1.5, z), 4);
            if (ground == null) continue;
            BlockState s = groundState(level, ground);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), x, ground.getY() + 1.05, z, 3, 0.1, 0.05, 0.1, 0.12);
            if (i % 3 == 0) level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, s), x, ground.getY() + 1.0, z, 1, 0, 0, 0, 0.05);
            if (i % 2 == 0) level.sendParticles(ElementFx.style(Element.EARTH, 1.0F), x, ground.getY() + 1.1, z, 1, 0.1, 0.05, 0.1, 0.15);
        }
        // v1.1: onda de terra marcando o anel
        ElementFx.shockwave(level, new Vec3(center.x, center.y + 0.1, center.z), Element.EARTH.color, (float) radius + 0.5F, 10, null);
    }

    /** Espiral de vento subindo em volta de um ponto. */
    public static void windSpiral(ServerLevel level, Vec3 base, double radius, double height, int points, double phase) {
        for (int i = 0; i < points; i++) {
            double t = i / (double) points;
            double a = phase + t * Math.PI * 4;
            double r = radius * (0.4 + 0.6 * t);
            level.sendParticles(Vfx.cloud(), base.x + Math.cos(a) * r, base.y + t * height, base.z + Math.sin(a) * r,
                    1, 0.02, 0.02, 0.02, 0.005);
            if (i % 3 == 0) {
                level.sendParticles(ElementFx.dust(Element.WIND, 1.0F), base.x + Math.cos(a) * r, base.y + t * height,
                        base.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                // risco de vento tangente à espiral (1 a cada 6 pontos: tornados chamam isto todo tick)
                if (i % 6 == 0) level.sendParticles(ElementFx.style(Element.WIND, 1.0F), base.x + Math.cos(a) * r, base.y + t * height,
                        base.z + Math.sin(a) * r, 0, -Math.sin(a), 0.15, Math.cos(a), 0.25);
            }
        }
    }

    /** Linha de vento (corte) entre dois pontos. */
    public static void windLine(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        int steps = Math.max(2, (int) (d.length() * 3));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(d.scale(i / (double) steps));
            level.sendParticles(ElementFx.dust(Element.WIND, 0.9F), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            if (i % 3 == 0) level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
        }
        // v1.1: riscos de vento ao longo do corte
        double len = d.length();
        if (len > 0.1) {
            for (int i = 0; i < Math.max(2, (int) (len * 0.8)); i++) {
                Vec3 p = from.add(d.scale(level.random.nextDouble()));
                level.sendParticles(ElementFx.style(Element.WIND, 1.0F), p.x, p.y, p.z, 0, d.x / len, d.y / len, d.z / len, 0.3);
            }
        }
    }

    /** Particula com velocidade (count = 0 faz o sendParticles usar dx/dy/dz como direcao). */
    public static void moving(ServerLevel level, ParticleOptions p, Vec3 pos, Vec3 velocity) {
        level.sendParticles(p, pos.x, pos.y, pos.z, 0, velocity.x, velocity.y, velocity.z, 1.0);
    }

    /** Sopro de vento saindo do corpo de uma entidade. */
    public static void gustAt(ServerLevel level, Entity e) {
        level.sendParticles(ParticleTypes.GUST, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(Vfx.cloud(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 8,
                e.getBbWidth() * 0.5, e.getBbHeight() * 0.3, e.getBbWidth() * 0.5, 0.05);
    }

    private EwFx() {}
}
