package com.renanpontes.isekaimahou.feature.fusions_b;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.Element;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Visual das fusões B: cada fusão mistura as partículas dos dois elementos-pai com o pó da cor da fusão
 * (Cristal = terra + luz, Astral = vento + luz, Chama Infernal = fogo + sombra, Abismo = água + sombra,
 * Gravidade = terra + sombra, Vazio = vento + sombra, Crepúsculo = luz + sombra).
 */
public final class FbFx {
    public static final int CRYSTAL = 0xE07AF0;
    public static final int CRYSTAL_LIGHT = 0xFFC8FF;
    public static final int ASTRAL = 0x9DB4FF;
    public static final int ASTRAL_LIGHT = 0xE6EEFF;
    public static final int HELLFIRE = 0xC0182E;
    public static final int HELL_BLACK = 0x1A0408;
    public static final int ABYSS = 0x2A4F9A;
    public static final int ABYSS_DEEP = 0x0B1A3A;
    public static final int GRAVITY = 0x6B5BA8;
    public static final int VOID = 0x5A2A8A;
    public static final int VOID_BLACK = 0x12061E;
    public static final int TWILIGHT = 0xC06AA0;
    public static final int TWILIGHT_GOLD = 0xFFD27A;
    public static final int TWILIGHT_SHADOW = 0x4A1E6E;
    public static final int VENOM = 0x1F8A6A;
    public static final int BLOOD = 0x8B0012;

    /** Cores do prisma (Prisma Refletor). */
    public static final int[] RAINBOW = {0xFF4040, 0xFF9A30, 0xFFE840, 0x50E060, 0x40A0FF, 0x9A5CFF};

    public static ParticleOptions dust(int rgb, float size) {
        // v1.3: brilho fx aditivo (mesmo tamanho do pó do vanilla; respeita o preset gráfico no cliente)
        return com.renanpontes.isekaimahou.core.ElementFx.glow(rgb, size);
    }

    public static DustColorTransitionOptions fade(int from, int to, float size) {
        return new DustColorTransitionOptions(vec(from), vec(to), Math.min(4F, size));
    }

    private static Vector3f vec(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
    }

    /** Partícula "pai A" e "pai B" de cada fusão. */
    public static ParticleOptions parentA(Element e) {
        return switch (e) {
            case CRYSTAL -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState());
            case ASTRAL -> Vfx.cloud();
            case HELLFIRE -> Vfx.flame();
            case ABYSS -> ParticleTypes.BUBBLE_POP;
            case GRAVITY -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState());
            case VOID -> Vfx.portal();
            case TWILIGHT -> Vfx.endRod();
            default -> Vfx.enchant();
        };
    }

    public static ParticleOptions parentB(Element e) {
        return switch (e) {
            case CRYSTAL -> Vfx.waxOn();
            case ASTRAL -> Vfx.endRod();
            case HELLFIRE -> Vfx.soulFlame();
            case ABYSS -> Vfx.ink();
            case GRAVITY -> Vfx.reversePortal();
            case VOID -> Vfx.reversePortal();
            case TWILIGHT -> Vfx.witch();
            default -> Vfx.enchant();
        };
    }

    public static ParticleOptions dustOf(Element e, float size) {
        return switch (e) {
            case CRYSTAL -> fade(CRYSTAL_LIGHT, CRYSTAL, size);
            case ASTRAL -> fade(ASTRAL_LIGHT, ASTRAL, size);
            case HELLFIRE -> fade(HELLFIRE, HELL_BLACK, size);
            case ABYSS -> fade(ABYSS, ABYSS_DEEP, size);
            case GRAVITY -> fade(GRAVITY, VOID_BLACK, size);
            case VOID -> fade(VOID, VOID_BLACK, size);
            case TWILIGHT -> fade(TWILIGHT_GOLD, TWILIGHT_SHADOW, size);
            default -> dust(e.color, size);
        };
    }

    /** Explosão de partículas misturando os dois pais. */
    public static void burst(ServerLevel level, Vec3 p, Element e, int count, double spread) {
        level.sendParticles(dustOf(e, 1.4F), p.x, p.y, p.z, count, spread, spread, spread, 0.0);
        level.sendParticles(parentA(e), p.x, p.y, p.z, Math.max(1, count / 3), spread, spread, spread, 0.05);
        level.sendParticles(parentB(e), p.x, p.y, p.z, Math.max(1, count / 3), spread, spread, spread, 0.03);
    }

    /** Partículas em volta do corpo de uma entidade. */
    public static void around(ServerLevel level, Entity e, ParticleOptions p, int count, double speed) {
        level.sendParticles(p, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), count,
                e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, e.getBbWidth() * 0.5, speed);
    }

    /** Anel horizontal. */
    public static void ring(ServerLevel level, Vec3 c, ParticleOptions p, double radius, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0.02, 0.02, 0.02, speed);
        }
    }

    /** Anel que se espalha para fora (velocidade radial). */
    public static void shockwave(ServerLevel level, Vec3 c, ParticleOptions p, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x, c.y, c.z, 0, Math.cos(a), 0.04, Math.sin(a), speed);
        }
    }

    /** Anel que se fecha para o centro (implosão). */
    public static void implode(ServerLevel level, Vec3 c, ParticleOptions p, double radius, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            double x = c.x + Math.cos(a) * radius, z = c.z + Math.sin(a) * radius;
            level.sendParticles(p, x, c.y, z, 0, -Math.cos(a), 0, -Math.sin(a), speed);
        }
    }

    /** Linha de partículas entre dois pontos. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double perBlock) {
        Vec3 d = to.subtract(from);
        int steps = Math.max(2, (int) (d.length() * perBlock));
        for (int i = 0; i <= steps; i++) {
            Vec3 q = from.add(d.scale(i / (double) steps));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0.01, 0.01, 0.01, 0);
        }
    }

    /** Coluna vertical (raios de luz/sombra). */
    public static void column(ServerLevel level, Vec3 base, ParticleOptions p, double height, int points) {
        for (int i = 0; i < points; i++) {
            double y = base.y + height * i / points;
            level.sendParticles(p, base.x, y, base.z, 1, 0.06, 0.1, 0.06, 0.0);
        }
    }

    /** Pontos aleatórios na superfície de uma esfera. */
    public static void sphere(ServerLevel level, Vec3 c, ParticleOptions p, double radius, int points) {
        var r = level.random;
        for (int i = 0; i < points; i++) {
            double u = r.nextDouble() * 2 - 1, th = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            level.sendParticles(p, c.x + s * Math.cos(th) * radius, c.y + u * radius, c.z + s * Math.sin(th) * radius, 1, 0, 0, 0, 0);
        }
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent s, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, s, SoundSource.PLAYERS, volume, pitch * (0.92F + level.random.nextFloat() * 0.16F));
    }

    private FbFx() {}
}
