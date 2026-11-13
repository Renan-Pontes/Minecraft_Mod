package com.renanpontes.isekaimahou.feature.origins_b;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Visual e som do Pacto (brasas, fumaça, carmesim) e da Necromancia (almas, ossos, ciano). Lado servidor. */
public final class ObFx {
    public static final int CRIMSON = 0x9E1B22;
    public static final int EMBER = 0xFF7A1A;
    public static final int VOID_RED = 0x3A0A10;
    public static final int VIOLET = 0x7A3FC0;
    public static final int SOUL_CYAN = 0x3FE0D0;
    public static final int BONE = 0xE8E0C8;

    private ObFx() {}

    public static ParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    public static ParticleOptions bone() {
        return new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.BONE));
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    // ------------------------------------------------------------------ Pacto
    /** Portal de brasas e fumaça (invocação de demônios). */
    public static void hellfireBurst(ServerLevel level, Vec3 at, double size) {
        level.sendParticles(Vfx.largeSmoke(), at.x, at.y + size * 0.5, at.z, (int) (12 * size), size * 0.35, size * 0.4, size * 0.35, 0.02);
        level.sendParticles(Vfx.flame(), at.x, at.y + size * 0.4, at.z, (int) (16 * size), size * 0.3, size * 0.4, size * 0.3, 0.05);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.2, at.z, (int) (4 * size), size * 0.3, 0.1, size * 0.3, 0);
        level.sendParticles(dust(CRIMSON, 1.6F), at.x, at.y + size * 0.5, at.z, (int) (14 * size), size * 0.4, size * 0.5, size * 0.4, 0);
    }

    /** Círculo de invocação no chão: anel carmesim com brasas subindo. */
    public static void pactCircle(ServerLevel level, Vec3 at, double radius) {
        int points = (int) (radius * 14);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            double x = at.x + Math.cos(a) * radius;
            double z = at.z + Math.sin(a) * radius;
            level.sendParticles(dust(i % 2 == 0 ? CRIMSON : EMBER, 1.1F), x, at.y + 0.08, z, 1, 0, 0, 0, 0);
            if (i % 4 == 0) level.sendParticles(ParticleTypes.SMALL_FLAME, x, at.y + 0.15, z, 1, 0.02, 0.1, 0.02, 0.01);
        }
        // Pentagrama simples: 5 linhas entre pontas
        for (int k = 0; k < 5; k++) {
            double a1 = Math.PI * 2 * k / 5 - Math.PI / 2;
            double a2 = Math.PI * 2 * ((k + 2) % 5) / 5 - Math.PI / 2;
            Vec3 p1 = new Vec3(at.x + Math.cos(a1) * radius, at.y + 0.08, at.z + Math.sin(a1) * radius);
            Vec3 p2 = new Vec3(at.x + Math.cos(a2) * radius, at.y + 0.08, at.z + Math.sin(a2) * radius);
            Vec3 d = p2.subtract(p1);
            int steps = (int) (d.length() * 5);
            for (int s = 0; s <= steps; s++) {
                Vec3 p = p1.add(d.scale(s / (double) Math.max(1, steps)));
                level.sendParticles(dust(CRIMSON, 0.8F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Puff de fumaça escura (sumir/aparecer). */
    public static void shadowPuff(ServerLevel level, Vec3 at, double height) {
        level.sendParticles(Vfx.largeSmoke(), at.x, at.y + height * 0.5, at.z, 14, 0.3, height * 0.35, 0.3, 0.02);
        level.sendParticles(Vfx.ink(), at.x, at.y + height * 0.5, at.z, 8, 0.3, height * 0.3, 0.3, 0.02);
        level.sendParticles(dust(VIOLET, 1.2F), at.x, at.y + height * 0.5, at.z, 10, 0.35, height * 0.35, 0.35, 0);
    }

    /**
     * Asas demoníacas desenhadas com partículas atrás dos ombros: 3 "dedos" ósseos por asa, com membrana,
     * batendo quando a criatura voa e recolhidas quando está no chão.
     */
    public static void demonWings(ServerLevel level, LivingEntity e) {
        double yaw = Math.toRadians(e.yBodyRot);
        // Base local: frente (fx, fz), lado direito (rx, rz)
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -fz, rz = fx;
        boolean flying = !e.onGround();
        double t = e.tickCount;
        double flap = flying ? 0.55 * Math.sin(t * 0.45) : -0.35;
        double spreadMul = flying ? 1.0 : 0.55;
        double scale = e.getBbHeight() / 1.8;
        Vec3 base = e.position().add(0, e.getBbHeight() * 0.74, 0).add(-fx * 0.28 * scale, 0, -fz * 0.28 * scale);
        ParticleOptions boneDust = dust(VOID_RED, 0.75F);
        ParticleOptions membrane = dust(CRIMSON, 0.6F);
        double[] angles = {0.85, 0.35, -0.15};
        double[] lengths = {1.25, 1.45, 1.15};
        for (int side = -1; side <= 1; side += 2) {
            Vec3 shoulder = base.add(rx * 0.18 * side * scale, 0, rz * 0.18 * side * scale);
            Vec3[] tips = new Vec3[3];
            for (int f = 0; f < 3; f++) {
                double ang = angles[f] + flap;
                double len = lengths[f] * scale * spreadMul;
                // Dedo: sai para o lado, sobe/desce pelo ângulo, e varre um pouco para trás
                Vec3 dir = new Vec3(rx * side * Math.cos(ang) - fx * 0.35, Math.sin(ang), rz * side * Math.cos(ang) - fz * 0.35).normalize();
                for (int s = 1; s <= 5; s++) {
                    Vec3 p = shoulder.add(dir.scale(len * s / 5.0));
                    level.sendParticles(boneDust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                tips[f] = shoulder.add(dir.scale(len));
            }
            // Membrana: pontos no meio de cada vão entre dois dedos
            for (int f = 0; f < 2; f++) {
                Vec3 gap = tips[f].add(tips[f + 1]).scale(0.5).subtract(shoulder);
                for (double s : new double[] {0.45, 0.75}) {
                    Vec3 p = shoulder.add(gap.scale(s));
                    level.sendParticles(membrane, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
                }
            }
            if (flying && e.tickCount % 6 == 0) {
                Vec3 tip = tips[1];
                level.sendParticles(Vfx.smallFlame(), tip.x, tip.y, tip.z, 1, 0.02, 0.02, 0.02, 0.0);
            }
        }
    }

    // ------------------------------------------------------------------ Necromancia
    /** Almas subindo e ossos espalhando (erguer mortos, desfazer servos). */
    public static void soulBurst(ServerLevel level, Vec3 at, double height) {
        level.sendParticles(Vfx.soul(), at.x, at.y + height * 0.5, at.z, 10, 0.3, height * 0.3, 0.3, 0.03);
        level.sendParticles(Vfx.soulFlame(), at.x, at.y + 0.2, at.z, 14, 0.35, 0.15, 0.35, 0.03);
        level.sendParticles(Vfx.sculkSoul(), at.x, at.y + height * 0.4, at.z, 6, 0.3, height * 0.25, 0.3, 0.02);
        level.sendParticles(dust(SOUL_CYAN, 1.1F), at.x, at.y + height * 0.5, at.z, 10, 0.3, height * 0.35, 0.3, 0);
    }

    public static void boneBurst(ServerLevel level, Vec3 at, int count, double spread) {
        level.sendParticles(bone(), at.x, at.y, at.z, count, spread, spread, spread, 0.15);
    }

    /** Terra se abrindo (algo emerge do chão). */
    public static void groundCrack(ServerLevel level, Entity e) {
        var state = level.getBlockState(e.blockPosition().below());
        if (!state.isAir()) {
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
                    e.getX(), e.getY() + 0.1, e.getZ(), 30, 0.4, 0.05, 0.4, 0.15);
        }
        level.sendParticles(Vfx.soulFlame(), e.getX(), e.getY() + 0.1, e.getZ(), 6, 0.35, 0.02, 0.35, 0.02);
    }

    /** Fio de almas indo de {@code from} até {@code to} (drenar, contrato cumprido). */
    public static void soulStream(ServerLevel level, Vec3 from, Vec3 to, int rgb) {
        Vec3 d = to.subtract(from);
        int steps = Mth.clamp((int) (d.length() * 3), 3, 60);
        for (int i = 0; i <= steps; i++) {
            double f = i / (double) steps;
            // Leve curva para cima no meio do caminho
            Vec3 p = from.add(d.scale(f)).add(0, Math.sin(f * Math.PI) * 0.6, 0);
            level.sendParticles(dust(rgb, 0.9F), p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
            if (i % 3 == 0) level.sendParticles(ParticleTypes.SOUL, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /** Aura da Forma de Lich: almas orbitando, chamas de alma nos pés, poeira ciano. */
    public static void lichAura(ServerLevel level, LivingEntity e) {
        double t = e.tickCount * 0.18;
        double h = e.getBbHeight();
        for (int i = 0; i < 3; i++) {
            double a = t + i * Math.PI * 2 / 3;
            double y = e.getY() + h * (0.35 + 0.3 * Math.sin(t * 0.7 + i));
            level.sendParticles(Vfx.soul(), e.getX() + Math.cos(a) * 0.9, y, e.getZ() + Math.sin(a) * 0.9, 1, 0, 0, 0, 0.0);
            level.sendParticles(dust(SOUL_CYAN, 0.8F), e.getX() + Math.cos(a + 0.4) * 0.85, y - 0.1, e.getZ() + Math.sin(a + 0.4) * 0.85,
                    1, 0, 0, 0, 0);
        }
        if (e.tickCount % 2 == 0) {
            level.sendParticles(Vfx.soulFlame(), e.getX(), e.getY() + 0.05, e.getZ(), 2, 0.3, 0.02, 0.3, 0.01);
        }
        if (e.tickCount % 5 == 0) {
            level.sendParticles(Vfx.sculkSoul(), e.getX(), e.getY() + h, e.getZ(), 1, 0.25, 0.1, 0.25, 0.02);
        }
    }

    /** Lascas de osso orbitando (Escudo de Ossos). */
    public static void boneOrbit(ServerLevel level, LivingEntity e) {
        double t = e.tickCount * 0.25;
        double r = Math.max(0.7, e.getBbWidth() * 0.9);
        for (int i = 0; i < 3; i++) {
            double a = t + i * Math.PI * 2 / 3;
            double y = e.getY() + e.getBbHeight() * (0.45 + 0.15 * Math.sin(t + i * 2));
            level.sendParticles(bone(), e.getX() + Math.cos(a) * r, y, e.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            level.sendParticles(dust(BONE, 0.7F), e.getX() + Math.cos(a - 0.3) * r, y, e.getZ() + Math.sin(a - 0.3) * r, 1, 0, 0, 0, 0);
        }
    }

    /** Selo do contrato flutuando sobre a cabeça do marcado. */
    public static void contractSigil(ServerLevel level, LivingEntity e) {
        double y = e.getY() + e.getBbHeight() + 0.45;
        double t = e.tickCount * 0.12;
        for (int i = 0; i < 8; i++) {
            double a = t + Math.PI * 2 * i / 8;
            level.sendParticles(dust(i % 2 == 0 ? CRIMSON : VOID_RED, 0.7F), e.getX() + Math.cos(a) * 0.4, y, e.getZ() + Math.sin(a) * 0.4,
                    1, 0, 0, 0, 0);
        }
        level.sendParticles(dust(EMBER, 0.9F), e.getX(), y + 0.05, e.getZ(), 1, 0, 0, 0, 0);
    }
}
