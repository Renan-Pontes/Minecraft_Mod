package com.renanpontes.isekaimahou.feature.fire_water.entity;

import java.util.EnumSet;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.fire_water.FwRegistries;
import com.renanpontes.isekaimahou.feature.fire_water.FwUtil;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Ondina invocada (Invocar Ondina): espírito da água que flutua perto do dono, cura os aliados feridos,
 * apaga o fogo deles e atira jatos d'água que encharcam os inimigos. Imune a água, resiste a fogo.
 */
public class SummonedUndine extends FwSummon {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.summoned_undine.idle");
    private static final RawAnimation MOVE = RawAnimation.begin().thenLoop("animation.summoned_undine.move");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.summoned_undine.cast");
    private static final RawAnimation HEAL = RawAnimation.begin().thenPlay("animation.summoned_undine.heal");

    private int healCooldown = 40;

    public SummonedUndine(EntityType<? extends SummonedUndine> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.45)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected boolean flies() { return true; }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new WaterJetGoal(this));
        goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.2, 6.0F, 2.5F));
        goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        addTargetGoals();
    }

    /** Voo igual ao do Allay: sem gravidade, com atrito no ar. */
    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance()) {
            if (isInWater()) {
                moveRelative(0.04F, input);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.8F));
            } else {
                moveRelative(getSpeed(), input);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.91F));
            }
        }
        calculateEntityAnimation(false);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    public boolean isPushedByFluid() { return false; }

    /** Espírito da água: não se encharca, não se afoga, não fica preso em bolha nem é levado por correnteza. */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.is(CoreRegistries.SOAKED) || effect.is(FwRegistries.WATER_CHOKE) || effect.is(FwRegistries.BUBBLE_PRISON)
                || effect.is(FwRegistries.UNDERTOW)) return false;
        return super.canBeAffected(effect);
    }

    // =====================================================================================
    //  Suporte: cura e apaga o fogo dos aliados
    // =====================================================================================

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (healCooldown > 0) {
            healCooldown--;
            return;
        }
        LivingEntity owner = getOwner();
        LivingEntity best = null;
        float bestRatio = 1.0F;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10),
                e -> e.isAlive() && (e == owner || isAlliedTo(e)) && !(e instanceof SummonedUndine))) {
            if (e.isOnFire()) {
                e.clearFire();
                ((ServerLevel) level()).sendParticles(Vfx.splash(), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
                        12, 0.3, 0.3, 0.3, 0.1);
            }
            float ratio = e.getHealth() / e.getMaxHealth();
            if (ratio < bestRatio - 0.02F && (best == null || ratio < bestRatio)) {
                best = e;
                bestRatio = ratio;
            }
        }
        if (best == null) {
            healCooldown = 10;
            return;
        }
        healAlly(best);
        healCooldown = bestRatio < 0.5F ? 40 : 60;
    }

    private void healAlly(LivingEntity target) {
        ServerLevel sl = (ServerLevel) level();
        target.heal(3.0F * power());
        triggerAnim("action", "heal");
        getLookControl().setLookAt(target);
        Vec3 from = position().add(0, getBbHeight() * 0.7, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 d = to.subtract(from);
        int steps = Math.max(4, (int) (d.length() * 4));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(d.scale(i / (double) steps));
            sl.sendParticles(FwUtil.dust(0x7FD8FF, 1.0F), p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
            if (i % 3 == 0) sl.sendParticles(ParticleTypes.SPLASH, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
        sl.sendParticles(ParticleTypes.HEART, to.x, to.y + target.getBbHeight() * 0.5, to.z, 3, 0.3, 0.2, 0.3, 0);
        FwUtil.around(sl, target, Vfx.water(), 10, 0);
        FwUtil.sound(sl, to, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.4F);
        FwUtil.sound(sl, to, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.6F, 1.3F);
    }

    // =====================================================================================
    //  Visual e som
    // =====================================================================================

    @Override
    protected void clientFx() {
        if (tickCount % 2 != 0) return;
        level().addParticle(Vfx.water(), getX() + (random.nextDouble() - 0.5) * 0.5, getY() + 0.3 + random.nextDouble() * 1.2,
                getZ() + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
        if (random.nextInt(3) == 0) {
            level().addParticle(FwUtil.dust(0x9FE6FF, 0.7F), getX() + (random.nextDouble() - 0.5) * 0.6, getY() + random.nextDouble() * 1.8,
                    getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0, 0);
        }
        if (random.nextInt(4) == 0) level().addParticle(ParticleTypes.FALLING_WATER, getX(), getY() + 0.1, getZ(), 0, 0, 0);
    }

    @Override
    protected void spawnFx(ServerLevel level) {
        Vec3 c = position().add(0, 0.2, 0);
        FwUtil.shockwave(level, c, Vfx.splash(), 32, 0.35);
        level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y + 1, c.z, 20, 0.4, 0.8, 0.4, 0.02);
        level.sendParticles(FwUtil.dust(0x7FD8FF, 1.4F), c.x, c.y + 1, c.z, 24, 0.4, 0.8, 0.4, 0);
        FwUtil.sound(level, c, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.8F, 1.2F);
        FwUtil.sound(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.9F);
    }

    @Override
    protected void vanishFx(ServerLevel level) {
        level.sendParticles(Vfx.splash(), getX(), getY() + 1, getZ(), 30, 0.3, 0.7, 0.3, 0.2);
        level.sendParticles(Vfx.water(), getX(), getY() + 1.2, getZ(), 16, 0.3, 0.5, 0.3, 0);
        FwUtil.sound(level, position(), SoundEvents.GENERIC_SPLASH, 0.8F, 1.3F);
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ALLAY_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.ALLAY_DEATH; }

    @Override
    public float getVoicePitch() { return super.getVoicePitch() * 0.8F; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6,
                state -> state.setAndContinue(state.isMoving() ? MOVE : IDLE)));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("cast", CAST)
                .triggerableAnim("heal", HEAL));
    }

    // =====================================================================================
    //  Ataque: jatos d'água a média distância
    // =====================================================================================

    static class WaterJetGoal extends Goal {
        private final SummonedUndine mob;
        private int cooldown = 15;
        private int repath;

        WaterJetGoal(SummonedUndine mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public void stop() { mob.getNavigation().stop(); }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (t == null) return;
            mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d = mob.distanceTo(t);
            boolean sees = mob.getSensing().hasLineOfSight(t);
            if (--repath <= 0) {
                repath = 10;
                if (d > 10 || !sees) {
                    mob.getNavigation().moveTo(t.getX(), t.getY() + 2.5, t.getZ(), 1.0);
                } else if (d < 4) {
                    // Mantém distância: recua flutuando para trás e para cima
                    Vec3 away = mob.position().subtract(t.position()).normalize().scale(4).add(0, 1.5, 0);
                    Vec3 dest = mob.position().add(away);
                    mob.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.1);
                } else {
                    mob.getNavigation().stop();
                }
            }
            if (cooldown > 0) {
                cooldown--;
                return;
            }
            if (d < 14 && sees) {
                shoot(t);
                cooldown = 30;
            }
        }

        private void shoot(LivingEntity t) {
            ServerLevel sl = (ServerLevel) mob.level();
            UndineWaterJet jet = new UndineWaterJet(sl, mob, (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE));
            Vec3 from = mob.getEyePosition().subtract(0, 0.25, 0).add(mob.getLookAngle().scale(0.4));
            jet.setPos(from.x, from.y, from.z);
            Vec3 to = t.position().add(0, t.getBbHeight() * 0.55, 0).subtract(from);
            jet.shoot(to.x, to.y, to.z, 1.35F, 1.0F);
            sl.addFreshEntity(jet);
            mob.triggerAnim("action", "cast");
            FwUtil.sound(sl, from, SoundEvents.DROWNED_SHOOT, 0.8F, 1.3F);
            FwUtil.sound(sl, from, SoundEvents.PLAYER_SPLASH, 0.4F, 1.6F);
        }
    }
}
