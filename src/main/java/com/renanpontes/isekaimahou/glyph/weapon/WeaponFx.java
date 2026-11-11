package com.renanpontes.isekaimahou.glyph.weapon;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Visual das técnicas de arma (servidor → partículas que todos por perto veem). */
public final class WeaponFx {

    /** Pó colorido (RGB) do tamanho pedido. */
    public static ParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F),
                Mth.clamp(size, 0.1F, 4.0F));
    }

    /** Frente horizontal do corpo. */
    private static Vec3 forward(LivingEntity e) {
        float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    /** Lado da mão principal (direita do corpo para destros). */
    private static Vec3 handSide(LivingEntity e) {
        float yaw = e.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return e.getMainArm() == HumanoidArm.LEFT ? right.scale(-1) : right;
    }

    /** Posição aproximada da mão principal no mundo. */
    public static Vec3 handPos(LivingEntity e) {
        float s = e.getScale();
        return e.position().add(handSide(e).scale(0.38 * s)).add(forward(e).scale(0.22 * s)).add(0, e.getBbHeight() * 0.42, 0);
    }

    /**
     * Brilho na arma: pó do elemento ao longo da lâmina (da mão até a ponta), chamado a cada 2 ticks enquanto a
     * técnica está "carregada" na arma.
     */
    public static void weaponGlow(ServerLevel level, LivingEntity e, Element element, int rgb) {
        Vec3 hand = handPos(e);
        Vec3 tip = hand.add(forward(e).scale(0.55 * e.getScale())).add(0, 0.5 * e.getScale(), 0);
        ParticleOptions d = dust(rgb, 0.7F);
        for (int i = 0; i < 3; i++) {
            Vec3 p = hand.lerp(tip, level.random.nextFloat());
            level.sendParticles(d, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        if (level.random.nextInt(4) == 0) {
            level.sendParticles(ElementFx.signature(element), tip.x, tip.y, tip.z, 1, 0.05, 0.05, 0.05, 0.01);
        }
    }

    /**
     * Rastro do golpe: um arco de luz na cor do elemento cortando a frente de quem atacou, até o alvo.
     */
    public static void slash(ServerLevel level, LivingEntity attacker, Vec3 target, Element element, int rgb) {
        Vec3 eye = attacker.getEyePosition();
        Vec3 dir = target.subtract(eye);
        if (dir.lengthSqr() < 1.0E-4) dir = attacker.getLookAngle();
        dir = dir.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(dir).normalize();
        double radius = Math.min(2.2, Math.max(1.2, eye.distanceTo(target) * 0.8));
        ParticleOptions big = dust(rgb, 1.1F);
        ParticleOptions light = dust(0xFFFFFF, 0.6F);
        int steps = 14;
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(-70 + 140.0 * i / steps);
            // diagonal: de cima-direita para baixo-esquerda
            Vec3 off = side.scale(Math.sin(a) * radius).add(up.scale(Math.sin(a) * -0.45 * radius))
                    .add(dir.scale(Math.cos(a) * radius));
            Vec3 p = eye.add(0, -0.25, 0).add(off);
            level.sendParticles(big, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
            if (i % 3 == 0) level.sendParticles(light, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.x, target.y, target.z, 1, 0, 0, 0, 0);
        ElementFx.burst(level, target, element, 10, 0.3);
    }

    /** Anel de pó no chão em volta de alguém (Postura, Aparar). */
    public static void groundRing(ServerLevel level, LivingEntity e, int rgb, double radius, int points) {
        ParticleOptions d = dust(rgb, 0.9F);
        double y = e.getY() + 0.1;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(d, e.getX() + Math.cos(a) * radius, y, e.getZ() + Math.sin(a) * radius, 1, 0, 0.02, 0, 0);
        }
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public static Vec3 center(net.minecraft.world.entity.Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    private WeaponFx() {}
}
