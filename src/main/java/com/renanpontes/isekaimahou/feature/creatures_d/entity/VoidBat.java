package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.AbilityGoal;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.FlyWanderGoal;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.SwoopAttackGoal;

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
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Morcego do Vazio: caçador das cavernas profundas e do Deep Dark. Mergulha mordendo (rouba vida e
 * corrompe), cospe pulsos do vazio e, quando ferido, costuma piscar para outro ponto da caverna.
 */
public class VoidBat extends FlyingElemental implements Enemy {
    private int blinkCooldown;

    public VoidBat(EntityType<? extends VoidBat> type, Level level) {
        super(type, level);
        this.xpReward = 7;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 14.0)
                .add(Attributes.FLYING_SPEED, 0.7)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public Element element() {
        return Element.SHADOW;
    }

    @Override
    protected String[] actionAnimations() {
        return new String[] {"attack", "cast"};
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new AbilityGoal(this, "cast", 3, 12, 12, 100, 160, false, t -> {
            ElementalBolt.shoot(this, t, Element.SHADOW, 3.0F, 1.0F, 3.0F, Items.ENDER_PEARL);
            playSoundHere(SoundEvents.ENDERMAN_AMBIENT, 0.6F, 1.8F);
        }).windup((t, left) -> {
            if (left % 3 == 0) serverParticles(ParticleTypes.REVERSE_PORTAL, position().add(0, 0.5, 0), 4, 0.3, 0.02);
        }));
        goalSelector.addGoal(2, new SwoopAttackGoal(this, 1.5, 20));
        goalSelector.addGoal(5, new FlyWanderGoal(this, 1.0, 1, 6, 10));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void onMeleeHit(LivingEntity target) {
        heal(2.0F);
        target.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 60, 0), this);
        serverParticles(ParticleTypes.REVERSE_PORTAL, target.position().add(0, target.getBbHeight() * 0.6, 0), 10, 0.3, 0.05);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide && isAlive() && blinkCooldown <= 0 && random.nextFloat() < 0.4F) blink();
        return r;
    }

    /** Pisca para um ponto livre próximo (como um enderman). */
    private void blink() {
        Vec3 from = position();
        for (int i = 0; i < 12; i++) {
            double dx = (random.nextDouble() - 0.5) * 16;
            double dy = random.nextInt(9) - 3;
            double dz = (random.nextDouble() - 0.5) * 16;
            if (level().noCollision(this, getBoundingBox().move(dx, dy, dz))
                    && level().getFluidState(blockPosition().offset((int) dx, (int) dy, (int) dz)).isEmpty()) {
                teleportTo(getX() + dx, getY() + dy, getZ() + dz);
                if (level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 0.4, from.z, 30, 0.3, 0.3, 0.3, 0.4);
                    ElementFx.burst(sl, position().add(0, 0.4, 0), Element.SHADOW, 12, 0.3);
                }
                level().playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, getSoundSource(), 0.8F, 1.5F);
                playSoundHere(SoundEvents.ENDERMAN_TELEPORT, 0.8F, 1.5F);
                blinkCooldown = 60;
                return;
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && blinkCooldown > 0) blinkCooldown--;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    protected void clientEffects() {
        if (random.nextInt(2) == 0) bodyParticle(ParticleTypes.PORTAL, 0.5, 0.4, 0);
        if (random.nextInt(6) == 0) bodyParticle(ElementFx.dust(Element.SHADOW, 0.6F), 0.6, 0.6, 0);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BAT_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6F;
    }
}
