package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.AbilityGoal;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Ondina: espírito de água humanoide dos rios e oceanos. Neutra — só revida (e chama as irmãs).
 * Lança jatos d'água que encharcam (magia de glifo "projétil + encharcar" quando existir) e, ferida,
 * entoa uma canção que cura a si e às ondinas próximas. Regenera devagar dentro d'água.
 */
public class Undine extends SwimmingElemental {
    public Undine(EntityType<? extends Undine> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.MOVEMENT_SPEED, 1.1)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public Element element() {
        return Element.WATER;
    }

    @Override
    protected String[] actionAnimations() {
        return new String[] {"attack", "cast", "special"};
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new AbilityGoal(this, "special", 0, 24, 20, 240, 320, true, t -> mend())
                .when(t -> getHealth() < getMaxHealth() * 0.6F).ignoreSight()
                .windup((t, left) -> {
                    if (level() instanceof ServerLevel sl && left % 4 == 0) {
                        MobMagic.converge(sl, position().add(0, 1.2, 0), ParticleTypes.BUBBLE, 1.6, 4);
                    }
                }));
        goalSelector.addGoal(2, new AbilityGoal(this, "cast", 2.5, 16, 14, 45, 80, true, this::waterBolt)
                .windup((t, left) -> {
                    if (left % 3 == 0) serverParticles(ParticleTypes.SPLASH, localPoint(0.3, 1.4, 0), 4, 0.2, 0.05);
                }));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(5, new RandomSwimmingGoal(this, 1.0, 40));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
    }

    private void waterBolt(LivingEntity target) {
        if (!MobMagic.cast(this, target, Element.WATER, 1.8F, "projectile", "soak")) {
            MobMagic.aim(this, target);
            ElementalBolt.shoot(this, target, Element.WATER, 4.0F, 1.0F, 2.0F, Items.HEART_OF_THE_SEA);
            playSoundHere(SoundEvents.PLAYER_SPLASH, 0.8F, 1.4F);
        }
    }

    /** Canção da maré: cura a si mesma e às ondinas próximas. */
    private void mend() {
        if (!(level() instanceof ServerLevel sl)) return;
        heal(8.0F);
        for (Undine sister : sl.getEntitiesOfClass(Undine.class, getBoundingBox().inflate(8), u -> u != this && u.isAlive())) {
            sister.heal(6.0F);
            ElementFx.line(sl, position().add(0, 1.2, 0), sister.position().add(0, 1.0, 0), Element.WATER);
        }
        ElementFx.ring(sl, position().add(0, 0.5, 0), Element.WATER, 2.0, 24);
        sl.sendParticles(ParticleTypes.HEART, getX(), getY() + 2.0, getZ(), 3, 0.4, 0.2, 0.4, 0);
        playSoundHere(SoundEvents.CONDUIT_ACTIVATE, 0.7F, 1.4F);
    }

    @Override
    protected void onMeleeHit(LivingEntity target) {
        target.clearFire();
        target.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 80, 0), this);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // Água viva: regenera devagar fora de combate
        if (!level().isClientSide && isInWater() && getTarget() == null && tickCount % 40 == 0 && getHealth() < getMaxHealth()) {
            heal(1.0F);
        }
    }

    @Override
    protected void clientEffects() {
        if (isInWater()) {
            if (random.nextInt(4) == 0) bodyParticle(ParticleTypes.BUBBLE, 0.7, 0.3, 0.05);
        } else if (random.nextInt(3) == 0) {
            bodyParticle(ParticleTypes.DRIPPING_WATER, 0.9, 0.3, 0);
        }
        if (random.nextInt(6) == 0) {
            Vec3 p = localPoint(0, 1.6, 0);
            level().addParticle(ElementFx.dust(Element.WATER, 0.7F), p.x + random.nextGaussian() * 0.3, p.y, p.z + random.nextGaussian() * 0.3, 0, 0, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isInWater() ? SoundEvents.DOLPHIN_AMBIENT_WATER : SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.75F;
    }
}
