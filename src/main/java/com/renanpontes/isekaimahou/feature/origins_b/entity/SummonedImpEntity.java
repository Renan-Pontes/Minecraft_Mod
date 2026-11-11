package com.renanpontes.isekaimahou.feature.origins_b.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.origins_b.ObFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Diabrete Invocado: diabinho alado que voa junto do ombro do dono e, em combate, paira acima do alvo
 * cuspindo bolinhas de fogo (dano de Fogo creditado ao dono). Voo livre por direção (como a Fada Guardiã).
 */
public class SummonedImpEntity extends ObSummonEntity {
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.summoned_imp.fly");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.summoned_imp.cast");

    private int shotCooldown = 20;
    private Vec3 home;

    public SummonedImpEntity(EntityType<? extends SummonedImpEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
        addTargetGoals();
    }

    @Override
    protected Element attackElement() {
        return Element.FIRE;
    }

    @Override
    protected boolean canFly() {
        return true;
    }

    // ------------------------------------------------------------------ voo e combate
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isRemoved()) return;
        ServerLevel sl = (ServerLevel) level();
        LivingEntity o = getOwner();
        LivingEntity target = getTarget();
        Vec3 anchor;
        if (o != null && !isStaying()) home = null;
        if (target != null && target.isAlive() && distanceToSqr(target) < 30 * 30) {
            // Circula acima do alvo, a uns 5 blocos
            double a = tickCount * 0.04 + getId() * 2.1;
            anchor = target.position().add(Math.cos(a) * 5.0, target.getBbHeight() + 2.5, Math.sin(a) * 5.0);
            getLookControl().setLookAt(target, 30F, 30F);
            if (--shotCooldown <= 0 && hasLineOfSight(target)) {
                shoot(sl, target);
                shotCooldown = 26 + random.nextInt(14);
            }
        } else if (o != null && !isStaying()) {
            // Paira atrás de um dos ombros do dono (sem tapar a visão)
            double yaw = Math.toRadians(o.getYRot() + (getId() % 2 == 0 ? 115 : -115));
            anchor = o.position().add(-Math.sin(yaw) * 1.2, o.getBbHeight() + 0.4 + Math.sin(tickCount * 0.12) * 0.2, Math.cos(yaw) * 1.2);
        } else {
            // Sem dono ou mandado ficar: ronda o ponto onde estava
            if (home == null) home = position();
            anchor = home.add(Math.cos(tickCount * 0.03) * 2, 1.5 + Math.sin(tickCount * 0.1) * 0.3, Math.sin(tickCount * 0.03) * 2);
        }
        Vec3 to = anchor.subtract(position());
        double d = to.length();
        double accel = Math.min(0.12, d * 0.05) * getAttributeValue(Attributes.FLYING_SPEED) / 0.6;
        if (d > 0.05) setDeltaMovement(getDeltaMovement().add(to.scale(accel / d)));
        if (target == null) {
            Vec3 face = getDeltaMovement();
            if (face.horizontalDistanceSqr() > 1.0E-3) {
                float yaw = (float) (Mth.atan2(face.z, face.x) * Mth.RAD_TO_DEG) - 90F;
                setYRot(Mth.rotLerp(0.3F, getYRot(), yaw));
                yBodyRot = getYRot();
            }
        } else {
            double dx = target.getX() - getX(), dz = target.getZ() - getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
            setYRot(Mth.rotLerp(0.4F, getYRot(), yaw));
            yBodyRot = getYRot();
        }
    }

    private void shoot(ServerLevel sl, LivingEntity target) {
        Vec3 from = position().add(0, getBbHeight() * 0.6, 0).add(getLookAngle().scale(0.4));
        Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from).normalize();
        // Um pouco de imprecisão: diabretes não são atiradores de elite
        dir = dir.add(random.nextGaussian() * 0.03, random.nextGaussian() * 0.03, random.nextGaussian() * 0.03).normalize();
        ImpFireballEntity ball = new ImpFireballEntity(sl, this, dir, 4.0F * power(), false);
        ball.setPos(from.x, from.y, from.z);
        sl.addFreshEntity(ball);
        triggerAnim("action", "cast");
        sl.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 5, 0.08, 0.08, 0.08, 0.03);
        playSound(SoundEvents.BLAZE_SHOOT, 0.6F, 1.6F + random.nextFloat() * 0.3F);
    }

    /** Diabretes não brigam de perto: só arranham quem encosta. */
    @Override
    protected void onMeleeHit(ServerLevel level, Entity target) {
        target.igniteForSeconds(2.0F);
    }

    /** Voo livre (como FlyingMob); o movimento vem do customServerAiStep. */
    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance()) {
            moveRelative(0.02F, input);
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.86));
        }
        calculateEntityAnimation(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && random.nextInt(3) == 0) {
            // Brasas caindo do corpo e fumacinha da cauda
            level().addParticle(random.nextInt(4) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.SMOKE,
                    getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 0.3 + random.nextDouble() * 0.4,
                    getZ() + (random.nextDouble() - 0.5) * 0.4, 0, -0.01, 0);
        }
    }

    @Override
    protected void dissolveFx(ServerLevel level) {
        ObFx.hellfireBurst(level, position(), 0.7);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.FIRE_EXTINGUISH, getSoundSource(), 0.6F, 1.5F);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl) ObFx.hellfireBurst(sl, position(), 0.7);
        super.die(source);
    }

    // ------------------------------------------------------------------ regras de voador
    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    // ------------------------------------------------------------------ sons
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.3F + random.nextFloat() * 0.2F;
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> state.setAndContinue(FLY)));
        controllers.add(new AnimationController<>(this, "action", 1, state -> PlayState.STOP).triggerableAnim("cast", CAST));
    }
}
