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
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomSwimmingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Serpente da Maré: caçadora dos oceanos. Persegue quem nada ou navega por perto, morde puxando para o
 * fundo e dispara um jato d'água que empurra, encharca e derruba o jogador do barco.
 */
public class TideSerpent extends SwimmingElemental implements Enemy {
    public TideSerpent(EntityType<? extends TideSerpent> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 36.0)
                .add(Attributes.MOVEMENT_SPEED, 1.2)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 3.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.FOLLOW_RANGE, 28.0);
    }

    @Override
    public Element element() {
        return Element.WATER;
    }

    @Override
    protected String[] actionAnimations() {
        return new String[] {"attack", "cast"};
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new AbilityGoal(this, "cast", 3, 18, 16, 80, 140, true, this::waterJet)
                .windup((t, left) -> {
                    if (left % 2 == 0) serverParticles(ParticleTypes.BUBBLE, localPoint(1.2, 0.4, 0), 3, 0.15, 0.05);
                }));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
        goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0, 20));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                p -> p.isInWater() || p.getVehicle() instanceof Boat));
    }

    /** Jato d'água em linha reta da boca até o alvo (bloqueado por paredes). */
    private void waterJet(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl)) return;
        MobMagic.aim(this, target);
        Vec3 from = localPoint(1.2, 0.45, 0);
        Vec3 to = MobMagic.center(target);
        HitResult wall = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
        ElementFx.line(sl, from, end, Element.WATER);
        MobMagic.line(sl, from, end, ParticleTypes.BUBBLE, 0.6);
        sl.sendParticles(ParticleTypes.SPLASH, end.x, end.y, end.z, 20, 0.4, 0.4, 0.4, 0.2);
        playSoundHere(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0F, 0.8F);
        if (wall.getType() != HitResult.Type.MISS) return;
        if (target.hurt(damageSources().indirectMagic(this, this), 5.0F)) {
            target.clearFire();
            target.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 120, 0), this);
            // Vira o barco: quem está navegando cai na água
            if (target.getVehicle() instanceof Boat) target.stopRiding();
            MobMagic.knockAway(target, position(), 1.0, 0.25);
        }
    }

    @Override
    protected void onMeleeHit(LivingEntity target) {
        target.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 100, 0), this);
        if (target.isInWater()) {
            target.setDeltaMovement(target.getDeltaMovement().add(0, -0.4, 0));
            target.hurtMarked = true;
        }
        playSoundHere(SoundEvents.EVOKER_FANGS_ATTACK, 0.8F, 1.3F);
    }

    @Override
    protected float dryDamage() {
        return 2.0F;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    protected void clientEffects() {
        if (isInWater() && random.nextInt(3) == 0) {
            Vec3 p = localPoint(-random.nextDouble() * 1.8, 0.4, 0);
            level().addParticle(ParticleTypes.BUBBLE, p.x, p.y, p.z, 0, 0.05, 0);
        }
        if (random.nextInt(10) == 0) {
            Vec3 p = localPoint(0.8, 0.5, 0);
            level().addParticle(ElementFx.dust(Element.WATER, 0.6F), p.x, p.y, p.z, 0, 0, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isInWater() ? SoundEvents.GUARDIAN_AMBIENT : SoundEvents.GUARDIAN_AMBIENT_LAND;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return isInWater() ? SoundEvents.GUARDIAN_HURT : SoundEvents.GUARDIAN_HURT_LAND;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return isInWater() ? SoundEvents.GUARDIAN_DEATH : SoundEvents.GUARDIAN_DEATH_LAND;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.8F;
    }
}
