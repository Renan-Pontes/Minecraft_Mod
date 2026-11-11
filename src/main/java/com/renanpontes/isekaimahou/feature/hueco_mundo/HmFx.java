package com.renanpontes.isekaimahou.feature.hueco_mundo;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Partículas e sons do módulo, sempre com orçamento fixo por chamada (nada de laços sem teto). */
public final class HmFx {
    /** Vermelho do Cero dos Vazios. */
    public static final int CERO_RED = 0xFF3550;
    /** Verde do Cero dos Arrancar de elite. */
    public static final int CERO_GREEN = 0x55FF8A;
    public static final int BONE = 0xEDE8DC;
    public static final int SAND = 0xE9ECF2;
    public static final int REISHI = 0x8FD8FF;
    public static final int FEY_PINK = 0xF29AD8;
    public static final int THORN = 0x5E8A3A;

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F),
                Math.max(0.1F, Math.min(4.0F, size)));
    }

    /** Anel horizontal (máx. 64 pontos). */
    public static void ring(ServerLevel level, Vec3 c, double radius, int points, ParticleOptions p, double speed) {
        points = Math.min(points, 64);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0.02, 0.02, 0.02, speed);
        }
    }

    /** Linha de partículas (máx. 60 pontos). */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double density) {
        Vec3 d = to.subtract(from);
        int steps = Math.min(60, Math.max(2, (int) (d.length() * density)));
        for (int i = 0; i <= steps; i++) {
            Vec3 at = from.add(d.scale(i / (double) steps));
            level.sendParticles(p, at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    /** Feixe grosso (Cero): núcleo claro + casca colorida + faíscas (máx. ~120 partículas). */
    public static void beam(ServerLevel level, Vec3 from, Vec3 to, int rgb, float width) {
        Vec3 d = to.subtract(from);
        int steps = Math.min(40, Math.max(3, (int) (d.length() * 1.5)));
        DustParticleOptions shell = dust(rgb, 1.6F + width);
        DustParticleOptions core = dust(0xFFFFFF, 1.0F + width * 0.5F);
        for (int i = 0; i <= steps; i++) {
            Vec3 at = from.add(d.scale(i / (double) steps));
            level.sendParticles(shell, at.x, at.y, at.z, 2, width * 0.25, width * 0.25, width * 0.25, 0.0);
            level.sendParticles(core, at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 12, 0.3, 0.3, 0.3, 0.12);
    }

    /** Casca esférica (máx. 80 pontos). */
    public static void sphere(ServerLevel level, Vec3 c, double radius, int points, ParticleOptions p) {
        points = Math.max(2, Math.min(points, 80));
        double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < points; i++) {
            double y = 1 - (i / (double) (points - 1)) * 2;
            double r = Math.sqrt(1 - y * y);
            double t = golden * i;
            level.sendParticles(p, c.x + Math.cos(t) * r * radius, c.y + y * radius, c.z + Math.sin(t) * r * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Coluna vertical de partículas (erupções de areia, espinhos). */
    public static void column(ServerLevel level, Vec3 base, double height, ParticleOptions p, int count) {
        count = Math.min(count, 48);
        for (int i = 0; i < count; i++) {
            double y = height * i / Math.max(1, count - 1);
            level.sendParticles(p, base.x, base.y + y, base.z, 1, 0.25, 0.05, 0.25, 0.01);
        }
    }

    /** "Pós-imagem" do Sonído: rastro de fantasmas entre dois pontos. */
    public static void afterimage(ServerLevel level, Vec3 from, Vec3 to, int rgb) {
        Vec3 d = to.subtract(from);
        for (int i = 0; i <= 5; i++) {
            Vec3 at = from.add(d.scale(i / 5.0));
            level.sendParticles(dust(rgb, 1.2F), at.x, at.y + 1.0, at.z, 6, 0.2, 0.5, 0.2, 0.0);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, to.x, to.y + 1.0, to.z, 1, 0, 0, 0, 0);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent s, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.HOSTILE, volume, pitch);
    }

    private HmFx() {}
}
