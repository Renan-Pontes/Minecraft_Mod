package com.renanpontes.isekaimahou.feature.origins_b.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.origins_b.ObFx;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Servo Morto-Vivo: cadáver erguido pela Necromancia (manto esfarrapado, olhos de alma). Emerge do chão,
 * segue o dono, luta corpo a corpo (dano de Sombra; às vezes enfraquece) e não queima no sol.
 * Também é o servo de presente do Rito Ossuário (permanente).
 */
public class UndeadServantEntity extends ObSummonEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.undead_servant.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.undead_servant.walk");
    private static final RawAnimation RISE = RawAnimation.begin().thenPlay("animation.undead_servant.rise");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.undead_servant.attack");

    public UndeadServantEntity(EntityType<? extends UndeadServantEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 26.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 4.5)
                .add(Attributes.ARMOR, 3.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(3, new FollowOwnerGoal(1.15, 5.0F, 2.5F));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return getOwnerUUID() == null && super.canUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        addTargetGoals();
    }

    @Override
    protected Element attackElement() {
        return Element.SHADOW;
    }

    @Override
    protected int arrivalLength() {
        return 30;
    }

    /** Sai da terra: blocos se partindo e chamas de alma. */
    @Override
    protected void arrivalTick(ServerLevel level, int ticksLeft) {
        if (ticksLeft % 4 == 0) ObFx.groundCrack(level, this);
        if (ticksLeft == 30) {
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ROOTED_DIRT_BREAK, getSoundSource(), 1.0F, 0.6F);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_INFECT, getSoundSource(), 0.8F, 0.7F);
        }
        if (ticksLeft == 6) level.playSound(null, getX(), getY(), getZ(), SoundEvents.ZOMBIE_AMBIENT, getSoundSource(), 1.0F, 0.6F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = super.doHurtTarget(target);
        if (ok) triggerAnim("action", "attack");
        return ok;
    }

    @Override
    protected void onMeleeHit(ServerLevel level, Entity target) {
        if (target instanceof LivingEntity le && random.nextInt(4) == 0) {
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), this);
        }
        level.sendParticles(Vfx.soul(), target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && random.nextInt(8) == 0) {
            // Olhos e manto soltando fiapos de alma
            level().addParticle(Vfx.soulFlame(), getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 1.6 + random.nextDouble() * 0.2,
                    getZ() + (random.nextDouble() - 0.5) * 0.4, 0, 0.005, 0);
        }
    }

    @Override
    protected void dissolveFx(ServerLevel level) {
        ObFx.soulBurst(level, position(), getBbHeight());
        ObFx.boneBurst(level, position().add(0, 1.0, 0), 10, 0.3);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_DEATH, getSoundSource(), 0.6F, 0.6F);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl) ObFx.soulBurst(sl, position(), getBbHeight());
        super.die(source);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.ZOMBIE_STEP, 0.15F, 1.0F);
    }

    @Override
    public float getVoicePitch() {
        return 0.7F + random.nextFloat() * 0.1F;
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (arrivalTicks() > 0) return state.setAndContinue(RISE);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 1, state -> PlayState.STOP).triggerableAnim("attack", ATTACK));
    }
}
