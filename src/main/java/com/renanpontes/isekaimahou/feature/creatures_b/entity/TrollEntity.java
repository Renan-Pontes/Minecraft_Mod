package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Troll: regenera muito rápido — a menos que tenha levado fogo/dano de fogo nos últimos 5 segundos.
 * Dano de fogo (vanilla ou magia de Fogo) causa o dobro. Fumaça = regeneração bloqueada.
 */
public class TrollEntity extends CbMonster {
    /** Ticks sem regenerar depois de queimar. */
    private static final int BURN_BLOCK = 100;
    private long lastBurn = -1000;

    public TrollEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override
    public CbFaction faction() {
        return CbFaction.TROLL;
    }

    @Override
    protected String animId() {
        return "troll";
    }

    @Override
    protected String[] actionAnims() {
        return new String[] {"attack", "roar"};
    }

    @Override
    protected float voicePitch() {
        return 0.6F;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, busyGoal());
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.75));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public boolean regenBlocked() {
        return level().getGameTime() - lastBurn < BURN_BLOCK || isOnFire();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        long now = level().getGameTime();
        boolean fire = source.is(DamageTypeTags.IS_FIRE);
        // Magia de Fogo: o núcleo marca o elemento antes do dano (o ×2 vem de ElementReactions.setMultiplier).
        Element spell = ElementReactions.lastElement(this, now, 1);
        if (fire) amount *= 2.0F;
        if (fire || (spell != null && spell.matches(Element.FIRE))) {
            if (!level().isClientSide && !regenBlocked()) {
                playSound(SoundEvents.FIRE_EXTINGUISH, 0.8F, 0.7F);
                playAction("roar");
            }
            lastBurn = now;
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isOnFire()) lastBurn = level().getGameTime();
        if (!(level() instanceof ServerLevel sl) || tickCount % 10 != 0) return;
        if (regenBlocked()) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.8, getZ(), 2, 0.4, 0.5, 0.4, 0.01);
        } else if (getHealth() < getMaxHealth()) {
            heal(1.5F);
            if (tickCount % 20 == 0) {
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.4, getZ(), 3, 0.5, 0.6, 0.5, 0);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("LastBurn", lastBurn);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        lastBurn = tag.getLong("LastBurn");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HOGLIN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HOGLIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HOGLIN_DEATH;
    }

    @Override
    protected void playAttackSound() {
        playSound(SoundEvents.POLAR_BEAR_WARNING, 1.0F, 0.6F);
    }
}
