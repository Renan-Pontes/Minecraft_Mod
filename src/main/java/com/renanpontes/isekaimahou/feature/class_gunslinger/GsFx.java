package com.renanpontes.isekaimahou.feature.class_gunslinger;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Efeitos de servidor do Pistoleiro: estampido, clarão do cano, fumaça, rastro de bala instantânea e sons de recarga. */
public final class GsFx {
    public static final int GOLD = 0xD9A441;

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F),
                Mth.clamp(size, 0.2F, 4.0F));
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Ponto do cano: um pouco à frente e à direita dos olhos (mão direita). side = 1 direita, -1 esquerda. */
    public static Vec3 muzzle(LivingEntity shooter, Vec3 look, int side) {
        float yaw = shooter.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return shooter.getEyePosition().add(look.scale(0.7)).add(right.scale(0.28 * side)).add(0, -0.18, 0);
    }

    /** Estampido + clarão: o tipo de arma muda o timbre (escopeta grave, pistola aguda, fuzil seco). */
    public static void shot(ServerLevel level, Vec3 muzzle, Vec3 dir, int rgb, GunKind kind) {
        float pitch = switch (kind) {
            case SHOTGUN -> 0.75F;
            case RIFLE -> 1.0F;
            case PISTOL, DUAL_PISTOLS -> 1.75F;
            default -> 1.45F;
        };
        float jitter = 0.9F + level.random.nextFloat() * 0.2F;
        sound(level, muzzle, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, kind == GunKind.SHOTGUN ? 0.9F : 0.7F, pitch * jitter);
        sound(level, muzzle, SoundEvents.AMETHYST_BLOCK_HIT, 0.6F, 1.6F + level.random.nextFloat() * 0.3F);
        if (kind == GunKind.RIFLE || kind == GunKind.SHOTGUN) sound(level, muzzle, SoundEvents.GENERIC_EXPLODE.value(), 0.25F, 1.9F);
        flash(level, muzzle, dir, rgb, kind == GunKind.SHOTGUN ? 1.6F : 1.0F);
    }

    /** Clarão do cano: chama, faíscas e fumaça saindo na direção do tiro. */
    public static void flash(ServerLevel level, Vec3 at, Vec3 dir, int rgb, float scale) {
        boolean low = reduced();
        level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(dust(rgb, 1.1F * scale), at.x, at.y, at.z, low ? 2 : 5, 0.05, 0.05, 0.05, 0);
        level.sendParticles(ParticleTypes.SMALL_FLAME, at.x + dir.x * 0.15, at.y + dir.y * 0.15, at.z + dir.z * 0.15,
                low ? 1 : 3, 0.04, 0.04, 0.04, 0.02);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, low ? 1 : 3, 0.05, 0.05, 0.05, 0.01);
        if (!low) {
            for (int i = 0; i < 3; i++) {
                double s = 0.15 + 0.1 * i;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x + dir.x * s, at.y + dir.y * s, at.z + dir.z * s, 1,
                        0.02, 0.02, 0.02, 0.02);
            }
        }
    }

    /** Rastro de bala instantânea (Bala Perfurante, Doze Badaladas): linha de pó brilhante. */
    public static void tracer(ServerLevel level, Vec3 from, Vec3 to, int rgb, float size) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.01) return;
        // orçamento: no máximo 40 pacotes de partícula por rastro (16 com partículas reduzidas)
        int steps = (int) Math.max(2, Math.min(reduced() ? 16 : 40, len * (reduced() ? 1.0 : 2.0)));
        DustParticleOptions dust = dust(rgb, size);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(d.scale(i / (double) steps));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 3, 0.05, 0.05, 0.05, 0.02);
    }

    /** Anel no chão (Doze Badaladas, duelo). */
    public static void ring(ServerLevel level, Vec3 center, double radius, int points, ParticleOptions p) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    /** Impacto da bala no bloco/alvo. */
    public static void impact(ServerLevel level, Vec3 at, int rgb, boolean entity) {
        level.sendParticles(dust(rgb, 0.9F), at.x, at.y, at.z, reduced() ? 2 : 6, 0.08, 0.08, 0.08, 0);
        level.sendParticles(entity ? ParticleTypes.CRIT : ParticleTypes.SMOKE, at.x, at.y, at.z, reduced() ? 1 : 4, 0.05, 0.05, 0.05,
                entity ? 0.25 : 0.01);
    }

    /** Sons da recarga: abrir o tambor, cartuchos e fechar com estalo. */
    public static void reloadStart(ServerLevel level, Vec3 at, GunKind kind) {
        sound(level, at, SoundEvents.CROSSBOW_LOADING_START.value(), 0.7F, kind == GunKind.SHOTGUN ? 0.8F : 1.3F);
        sound(level, at, SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.6F, 1.4F);
    }

    public static void reloadEnd(ServerLevel level, Vec3 at, GunKind kind) {
        sound(level, at, SoundEvents.CROSSBOW_LOADING_END.value(), 0.8F, kind == GunKind.SHOTGUN ? 0.9F : 1.5F);
        sound(level, at, SoundEvents.IRON_TRAPDOOR_CLOSE, 0.5F, 1.8F);
    }

    public static void empty(ServerLevel level, Vec3 at) {
        sound(level, at, SoundEvents.DISPENSER_FAIL, 0.6F, 1.8F);
    }

    public static boolean reduced() {
        try {
            return MahouConfig.REDUCED_PARTICLES.get();
        } catch (RuntimeException e) {
            return false; // config ainda não carregada (servidor dedicado no início)
        }
    }

    private GsFx() {}
}
