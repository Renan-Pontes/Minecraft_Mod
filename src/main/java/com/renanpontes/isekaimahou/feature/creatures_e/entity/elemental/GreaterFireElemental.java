package com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental;

import java.util.List;

import com.renanpontes.isekaimahou.feature.creatures_e.CeFx;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.BoltKind;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.CreatureMove;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalBolt;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalCreature;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * Elemental de Fogo Maior (Ilhas de Brasa): torso de magma sobre um vortice de chamas.
 * Socos incendiarios, rajada de 3 bolas de magma e uma Nova de Fogo quando cercado. Sofre com agua e chuva.
 */
public class GreaterFireElemental extends DimensionalCreature implements Enemy {
    private int volleySide = 1;

    public GreaterFireElemental(EntityType<? extends GreaterFireElemental> type, Level level) {
        super(type, level);
        setPathfindingMalus(PathType.WATER, -1.0F);
        setPathfindingMalus(PathType.LAVA, 8.0F);
        setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
        setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(90, 8, 11, 0.24)
                .add(Attributes.ARMOR_TOUGHNESS, 2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public Faction faction() {
        return Faction.ELEMENTAL;
    }

    @Override
    protected int experience() {
        return 25;
    }

    @Override
    public double preferredRange() {
        return 4.5;
    }

    @Override
    protected List<CreatureMove> createMoves() {
        return List.of(
                CreatureMove.of("nova").timing(18, 34).cooldown(200).range(0, 4.0).blind()
                        .when(t -> foesNear(position(), 5).size() >= 2 || getHealth() < getMaxHealth() * 0.5F)
                        .onStart(t -> CeFx.sound(level(), position(), SoundEvents.BLAZE_BURN, 1.5F, 0.6F))
                        .perform(t -> nova()),
                CreatureMove.of("attack").timing(9, 18).cooldown(26).range(0, 2.2).perform(this::punch),
                CreatureMove.of("cast").timing(12, 30).cooldown(70).range(2.5, 22)
                        .during((t, i) -> {
                            if (i == 12 || i == 17 || i == 22) shootMagma(t);
                        }));
    }

    private void punch(LivingEntity t) {
        if (!(level() instanceof ServerLevel level)) return;
        if (gapTo(t) > 3.0) {
            CeFx.sound(level, position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 0.6F);
            return;
        }
        if (meleeHit(t, 1.0F, 0.9)) t.igniteForSeconds(5);
        Vec3 at = center(t);
        CeFx.burst(level, at, ParticleTypes.FLAME, 20, 0.35, 0.08);
        CeFx.burst(level, at, ParticleTypes.LAVA, 3, 0.2, 0.0);
        CeFx.sound(level, at, SoundEvents.FIRECHARGE_USE, 1.0F, 0.7F);
        CeFx.sound(level, at, SoundEvents.IRON_GOLEM_ATTACK, 0.8F, 0.8F);
    }

    /** Bola de magma lancada de uma das maos (alterna os lados). */
    private void shootMagma(LivingEntity t) {
        if (!(level() instanceof ServerLevel level)) return;
        volleySide = -volleySide;
        double yaw = Math.toRadians(yBodyRot);
        Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw)).scale(0.9 * volleySide);
        Vec3 from = position().add(0, 2.1, 0).add(side).add(getLookAngle().scale(0.6));
        DimensionalBolt.fire(this, BoltKind.MAGMA, from, center(t), 1.15F, 8.0F, 3.0F);
        CeFx.burst(level, from, ParticleTypes.FLAME, 8, 0.15, 0.04);
        CeFx.sound(level, from, SoundEvents.BLAZE_SHOOT, 1.0F, 0.7F);
    }

    /** Nova de Fogo: anel de chamas que queima e empurra tudo ao redor. */
    private void nova() {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 c = position().add(0, 0.4, 0);
        CeFx.shockwave(level, c, ParticleTypes.FLAME, 64, 0.45);
        CeFx.shockwave(level, c.add(0, 0.6, 0), ParticleTypes.LARGE_SMOKE, 32, 0.3);
        CeFx.burst(level, c.add(0, 1.2, 0), ParticleTypes.LAVA, 14, 0.8, 0.0);
        CeFx.burst(level, c, ParticleTypes.EXPLOSION, 2, 0.5, 0.0);
        CeFx.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.2F);
        CeFx.sound(level, c, SoundEvents.BLAZE_SHOOT, 1.5F, 0.5F);
        for (LivingEntity e : foesNear(c, 5.5)) {
            if (strike(e, 9.0F, magicSource(this))) e.igniteForSeconds(6);
            e.knockback(1.3, getX() - e.getX(), getZ() - e.getZ());
            e.hurtMarked = true;
        }
    }

    @Override
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    protected void clientFx() {
        var r = random;
        for (int i = 0; i < 2; i++) {
            level().addParticle(ParticleTypes.FLAME, getRandomX(0.45), getY() + r.nextDouble() * 0.9, getRandomZ(0.45),
                    0, 0.03 + r.nextDouble() * 0.03, 0);
        }
        if (r.nextInt(3) == 0) level().addParticle(ParticleTypes.LARGE_SMOKE, getRandomX(0.4), getY() + 2.9, getRandomZ(0.4), 0, 0.03, 0);
        if (r.nextInt(12) == 0) level().addParticle(ParticleTypes.LAVA, getRandomX(0.6), getY() + 1.8, getRandomZ(0.6), 0, 0, 0);
        if (r.nextInt(6) == 0) level().addParticle(ParticleTypes.DRIPPING_LAVA, getRandomX(0.7), getY() + 1.5, getRandomZ(0.7), 0, 0, 0);
        // Nova carregando: chamas sugadas para o nucleo
        if ("nova".equals(actionName()) && actionAge() < 18) {
            for (int i = 0; i < 4; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = 3.0 + r.nextDouble();
                level().addParticle(ParticleTypes.FLAME, getX() + Math.cos(a) * d, getY() + 0.3 + r.nextDouble(), getZ() + Math.sin(a) * d,
                        -Math.cos(a) * d * 0.08, 0.06, -Math.sin(a) * d * 0.08);
            }
        }
    }

    @Override
    protected void deathFx(ServerLevel level) {
        Vec3 c = position().add(0, 1.4, 0);
        CeFx.burst(level, c, ParticleTypes.FLAME, 40, 0.7, 0.08);
        CeFx.burst(level, c, ParticleTypes.LARGE_SMOKE, 20, 0.6, 0.04);
        CeFx.burst(level, c, ParticleTypes.LAVA, 8, 0.5, 0.0);
        CeFx.sound(level, c, SoundEvents.LAVA_EXTINGUISH, 1.2F, 0.6F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BLAZE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BLAZE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLAZE_DEATH;
    }
}
