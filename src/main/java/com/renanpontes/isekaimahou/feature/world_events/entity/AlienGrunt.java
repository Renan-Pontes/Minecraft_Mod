package com.renanpontes.isekaimahou.feature.world_events.entity;

import java.util.List;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * Batedor Estelar: o soldado raso da invasão. Corre para cima e bate com as garras; de vez em quando o golpe
 * deixa a vítima lenta por um instante (a gosma do espaço gruda).
 */
public class AlienGrunt extends AlienMob {
    public AlienGrunt(EntityType<? extends AlienGrunt> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 14.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected String modelId() {
        return "world_events_alien_grunt";
    }

    @Override
    protected List<String> actions() {
        return List.of("attack");
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, false));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        action("attack");
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity le && random.nextFloat() < 0.25F) {
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), this);
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F + random.nextFloat() * 0.15F;
    }
}
