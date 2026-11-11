package com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;

/**
 * Cavaleiro de lobo: um goblin montado num lobo grande (uma entidade só). Muito rápido, salta no alvo
 * e ataca com mordida + lança. Também foge com pouca vida — e lobo foge rápido.
 */
public class GoblinWolfRider extends AbstractGoblin {
    public GoblinWolfRider(EntityType<? extends GoblinWolfRider> type, Level level) {
        super(type, level);
        this.xpReward = 9;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.ATTACK_DAMAGE, 4.5)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.3, true));
    }

    @Override
    protected void equipGear(RandomSource random, DifficultyInstance difficulty) {
        // A lança e as rédeas fazem parte do modelo.
    }

    @Override
    public boolean canSteal() {
        return false;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = super.doHurtTarget(target);
        if (ok && target instanceof LivingEntity le) {
            // Mordida do lobo: segura a vítima por um instante.
            le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 1), this);
        }
        return ok;
    }

    @Override
    protected PlayState movePredicate(AnimationState<AbstractGoblin> state) {
        if (state.isMoving()) return state.setAndContinue(getDeltaMovement().horizontalDistanceSqr() > 0.012 ? RUN : WALK);
        return state.setAndContinue(IDLE);
    }

    @Override
    protected PlayState armsPredicate(AnimationState<AbstractGoblin> state) {
        return PlayState.STOP;
    }

    @Override
    protected float voiceBase() {
        return 1.0F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        if (isFleeing()) return SoundEvents.WOLF_WHINE;
        return isAggressive() ? SoundEvents.WOLF_GROWL : (random.nextInt(3) == 0 ? SoundEvents.PIGLIN_AMBIENT : SoundEvents.WOLF_PANT);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOLF_STEP, 0.2F, 0.9F);
    }
}
