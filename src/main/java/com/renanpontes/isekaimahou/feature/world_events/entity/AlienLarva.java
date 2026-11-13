package com.renanpontes.isekaimahou.feature.world_events.entity;

import java.util.List;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/** Larva Estelar: pequena, rápida e em bando. Pula na cara de quem chega perto; morre com um tapa. */
public class AlienLarva extends AlienMob {
    public AlienLarva(EntityType<? extends AlienLarva> type, Level level) {
        super(type, level);
        this.xpReward = 3;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 5.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected String modelId() {
        return "world_events_alien_larva";
    }

    @Override
    protected List<String> actions() {
        return List.of("attack");
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.42F));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, false));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        action("attack");
        return super.doHurtTarget(target);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SILVERFISH_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SILVERFISH_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SILVERFISH_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F + random.nextFloat() * 0.2F;
    }
}
