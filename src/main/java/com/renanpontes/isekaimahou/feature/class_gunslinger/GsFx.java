package com.renanpontes.isekaimahou.feature.class_gunslinger;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
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
        GsFxNet.at(level, GsFxNet.MUZZLE, muzzle, dir, kind == GunKind.SHOTGUN ? 1.3F : 1.0F, rgb, -1, kind.ordinal());
    }

    /** Clarão do cano: chama, faíscas e fumaça saindo na direção do tiro. */
    public static void flash(ServerLevel level, Vec3 at, Vec3 dir, int rgb, float scale) {
        // o clarão é desenhado no cliente (GsFxClient), respeitando o preset gráfico
        GsFxNet.at(level, GsFxNet.MUZZLE, at, dir, scale, rgb, -1, GunKind.PISTOL.ordinal());
    }

    /** Rastro de bala instantânea (Bala Perfurante, Doze Badaladas): linha de pó brilhante. */
    public static void tracer(ServerLevel level, Vec3 from, Vec3 to, int rgb, float size) {
        GsFxNet.at(level, GsFxNet.TRACER, from, to, size, rgb, -1);
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
        GsFxNet.at(level, GsFxNet.IMPACT, at, Vec3.ZERO, 1F, rgb, -1, entity ? 1 : 0);
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
        return !FxQuality.atLeast(MahouConfig.Graphics.HIGH);
    }

    private GsFx() {}
}
