package com.renanpontes.isekaimahou.feature.hueco_mundo.entity;

import java.util.List;

import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Vazio Alado: raia de ossos com asas de membrana negra. Circula alto disparando "Balas" (rajadas brancas rápidas) e, de
 * vez em quando, mergulha em linha reta (a linha aparece no chão antes).
 */
public class HollowWraith extends HmFlyer {
    private int shotCd = 40, diveCd = 120, diveTicks;
    private Vec3 diveDir = Vec3.ZERO;

    public HollowWraith(EntityType<? extends HollowWraith> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 24).add(Attributes.ARMOR, 2)
                .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FLYING_SPEED, 0.55).add(Attributes.ATTACK_DAMAGE, 6)
                .add(Attributes.FOLLOW_RANGE, 40);
    }

    @Override
    public String modelId() {
        return "hollow_wraith";
    }

    @Override
    public HmRealm side() {
        return HmRealm.HOLLOW;
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("shoot", "dive");
    }

    @Override
    protected double orbitHeight() {
        return 5.5;
    }

    @Override
    protected double orbitRadius() {
        return 7.0;
    }

    @Override
    protected void combatTick(LivingEntity t) {
        ServerLevel sl = (ServerLevel) level();
        if (diveTicks > 0) {
            diveTicks--;
            moveToward(position().add(diveDir.scale(4)), 2.6);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.6), this::isFoe)) {
                if (e.hurt(damageSources().mobAttack(this), 8F)) diveTicks = 0;
            }
            return;
        }
        if (--shotCd <= 0 && hasLineOfSight(t)) {
            shotCd = 35 + random.nextInt(25);
            action("shoot");
            HmBolt.shoot(this, getEyePosition(), t.getEyePosition(), HmBolt.BALA, 4.5F, 1.4F);
            playSound(SoundEvents.BLAZE_SHOOT, 0.6F, 1.6F);
        }
        if (--diveCd <= 0 && hasLineOfSight(t)) {
            diveCd = 160 + random.nextInt(60);
            Vec3 from = position();
            Vec3 aim = t.position().subtract(from);
            diveDir = aim.normalize();
            Vec3 groundFrom = new Vec3(from.x, t.getY(), from.z);
            Vec3 to = groundFrom.add(aim.multiply(1, 0, 1).normalize().scale(aim.horizontalDistance() + 4));
            Telegraph.show(sl, Telegraph.Shape.line(groundFrom, to, 2.0F).warmup(12).color(Telegraph.WARNING).owner(this).noRunes());
            action("dive");
            playSound(SoundEvents.PHANTOM_SWOOP, 1.0F, 0.8F);
            diveTicks = -12;
        }
        if (diveTicks < 0) moveToward(position(), 0.3); // paira parado enquanto a linha enche
        if (diveTicks < 0 && ++diveTicks == 0) {
            diveTicks = 16;
            sl.sendParticles(HmFx.dust(HmFx.BONE, 1.2F), getX(), getY(), getZ(), 10, 0.4, 0.2, 0.4, 0);
        }
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F;
    }
}
