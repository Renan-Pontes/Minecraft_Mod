package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Utilidades de combate/efeitos dos humanoides (ondas de choque, poeira do chão, sons). */
public final class CbFx {

    /** Inimigos vivos (não aliados, não criativos) dentro de uma esfera. */
    public static List<LivingEntity> enemies(Mob source, Vec3 center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : source.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e != source && e.isAlive() && !source.isAlliedTo(e) && e.distanceToSqr(center) <= radius * radius)) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            out.add(e);
        }
        return out;
    }

    /**
     * Onda de choque no chão: dano físico do mob a todos os inimigos no raio, empurrando para fora e para cima.
     * @return quantos foram atingidos
     */
    public static int shockwave(Mob source, Vec3 center, double radius, float damage, double knockOut, double knockUp) {
        int hits = 0;
        for (LivingEntity e : enemies(source, center, radius)) {
            if (e.hurt(source.damageSources().mobAttack(source), damage)) hits++;
            Vec3 d = e.position().subtract(center);
            d = d.lengthSqr() < 1.0E-4 ? source.getLookAngle() : d.normalize();
            double resist = 1.0 - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            e.push(d.x * knockOut * resist, knockUp * resist, d.z * knockOut * resist);
            e.hurtMarked = true;
        }
        if (source.level() instanceof ServerLevel level) groundBurst(level, center, radius);
        return hits;
    }

    /** Poeira do bloco do chão em anel + explosão no centro. */
    public static void groundBurst(ServerLevel level, Vec3 center, double radius) {
        ParticleOptions dust = groundParticle(level, BlockPos.containing(center.x, center.y - 0.5, center.z));
        int points = (int) (radius * 10);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            for (double r = 0.8; r <= radius; r += 1.2) {
                level.sendParticles(dust, center.x + Math.cos(a) * r, center.y + 0.15, center.z + Math.sin(a) * r,
                        2, 0.15, 0.05, 0.15, 0.15);
            }
        }
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.3, center.z, 2, 0.4, 0.1, 0.4, 0);
        level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.2, center.z, 12, radius * 0.3, 0.1, radius * 0.3, 0.04);
    }

    public static ParticleOptions groundParticle(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) state = level.getBlockState(pos.below());
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) return ParticleTypes.POOF;
        return new BlockParticleOption(ParticleTypes.BLOCK, state);
    }

    public static void sound(Mob source, SoundEvent sound, float volume, float pitch) {
        source.level().playSound(null, source.getX(), source.getY(), source.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** Ponto no chão {@code dist} blocos à frente do mob. */
    public static Vec3 ahead(Mob mob, double dist) {
        Vec3 look = Vec3.directionFromRotation(0, mob.getYRot());
        return mob.position().add(look.scale(dist));
    }

    /** Faz o mob encarar o alvo instantaneamente (corpo e cabeça). */
    public static void face(Mob mob, LivingEntity target) {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180 / Math.PI)) - 90.0F;
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    /** Bônus permanente de atributo (salvo com a entidade), aplicado uma vez só. */
    public static void permanentBonus(LivingEntity e, Holder<Attribute> attr, ResourceLocation id, double multiplier) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null && !inst.hasModifier(id)) {
            inst.addPermanentModifier(new AttributeModifier(id, multiplier, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private CbFx() {}
}
