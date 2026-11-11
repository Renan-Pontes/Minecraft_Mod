package com.renanpontes.isekaimahou.feature.origins_b.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.origins_b.ObFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Guerreiro de Ossos (Exército de Ossos): esqueleto com elmo, espada e escudo de osso. Rápido e disciplinado:
 * apara com o escudo parte dos golpes e flechas que vêm de frente.
 */
public class BoneWarriorEntity extends ObSummonEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.bone_warrior.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.bone_warrior.walk");
    private static final RawAnimation ASSEMBLE = RawAnimation.begin().thenPlay("animation.bone_warrior.assemble");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.bone_warrior.attack");
    private static final RawAnimation BLOCK = RawAnimation.begin().thenPlay("animation.bone_warrior.block");

    private int blockCooldown;

    public BoneWarriorEntity(EntityType<? extends BoneWarriorEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 22.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
        goalSelector.addGoal(3, new FollowOwnerGoal(1.25, 5.0F, 2.5F));
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
        return 20;
    }

    /** Os ossos se juntam: redemoinho de lascas e chamas de alma. */
    @Override
    protected void arrivalTick(ServerLevel level, int ticksLeft) {
        double a = ticksLeft * 0.6;
        double h = (20 - ticksLeft) / 20.0 * getBbHeight();
        for (int i = 0; i < 2; i++) {
            double ang = a + i * Math.PI;
            level.sendParticles(ObFx.bone(), getX() + Math.cos(ang) * 0.7, getY() + h, getZ() + Math.sin(ang) * 0.7, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + h, getZ(), 1, 0.2, 0.05, 0.2, 0.01);
        if (ticksLeft % 5 == 0) level.playSound(null, getX(), getY(), getZ(), SoundEvents.BONE_BLOCK_PLACE, getSoundSource(), 0.7F, 1.2F);
        if (ticksLeft == 2) level.playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_AMBIENT, getSoundSource(), 1.0F, 0.8F);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (blockCooldown > 0) blockCooldown--;
    }

    /** Escudo: 35% de chance de aparar golpes/flechas de frente (sem dano). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isFriendlyFire(source)) return false;
        if (!level().isClientSide && blockCooldown <= 0 && arrivalTicks() <= 0 && source.getSourcePosition() != null
                && !source.is(DamageTypeTags.BYPASSES_SHIELD) && random.nextFloat() < 0.35F) {
            Vec3 to = source.getSourcePosition().subtract(position()).multiply(1, 0, 1);
            Vec3 facing = Vec3.directionFromRotation(0, yBodyRot);
            if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(facing) > 0.3) {
                blockCooldown = 30;
                triggerAnim("action", "block");
                playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.9F + random.nextFloat() * 0.2F);
                if (level() instanceof ServerLevel sl) ObFx.boneBurst(sl, position().add(facing.scale(0.5)).add(0, 1.1, 0), 5, 0.15);
                if (source.isDirect() && source.getDirectEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
                    attacker.knockback(0.4, getX() - attacker.getX(), getZ() - attacker.getZ());
                }
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = super.doHurtTarget(target);
        if (ok) triggerAnim("action", "attack");
        return ok;
    }

    @Override
    protected void onMeleeHit(ServerLevel level, Entity target) {
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, getSoundSource(), 0.7F, 1.1F);
    }

    @Override
    protected void dissolveFx(ServerLevel level) {
        ObFx.boneBurst(level, position().add(0, 1.0, 0), 18, 0.35);
        level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.0, getZ(), 4, 0.2, 0.4, 0.2, 0.03);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SKELETON_DEATH, getSoundSource(), 0.7F, 1.1F);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BONE_BLOCK_BREAK, getSoundSource(), 1.0F, 0.9F);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl) ObFx.boneBurst(sl, position().add(0, 1.0, 0), 18, 0.35);
        super.die(source);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SKELETON_STEP, 0.15F, 1.0F);
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (arrivalTicks() > 0) return state.setAndContinue(ASSEMBLE);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 1, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK).triggerableAnim("block", BLOCK));
    }
}
