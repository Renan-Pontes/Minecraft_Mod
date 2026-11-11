package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.AbilityGoal;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.FlyWanderGoal;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.KeepRangeGoal;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;

/**
 * Sentinela Solar: construto dourado que flutua nos desertos durante o dia. Carrega (1,5 s, com a luz
 * convergindo e uma linha de mira no alvo) e dispara um raio de sol que cega e queima; de perto solta
 * uma explosão solar. À noite adormece e afunda na areia — só desperta se for atacada.
 */
public class SolarSentinel extends FlyingElemental implements Enemy {
    private static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(SolarSentinel.class, EntityDataSerializers.BOOLEAN);

    public SolarSentinel(EntityType<? extends SolarSentinel> type, Level level) {
        super(type, level);
        this.xpReward = 14;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 32.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FLYING_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    public Element element() {
        return Element.LIGHT;
    }

    @Override
    protected String[] actionAnimations() {
        return new String[] {"attack", "cast"};
    }

    @Override
    protected String loopAnimation(boolean moving) {
        if (isDormant()) return "dormant";
        return moving ? "move" : "idle";
    }

    @Override
    protected boolean isAnimMoving(AnimationState<ElementalCreature> state) {
        return getDeltaMovement().horizontalDistanceSqr() > 4.0E-4;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DORMANT, false);
    }

    public boolean isDormant() {
        return entityData.get(DORMANT);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new AbilityGoal(this, "attack", 0, 3.2, 8, 40, 60, true, t -> solarFlare()));
        goalSelector.addGoal(2, new AbilityGoal(this, "cast", 3, 22, 30, 70, 110, true, this::solarRay)
                .windup(this::chargeRay));
        goalSelector.addGoal(3, new KeepRangeGoal(this, 1.0, 6, 14, 1.5));
        goalSelector.addGoal(5, new FlyWanderGoal(this, 0.8, 1, 3, 8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, p -> !isDormant()));
    }

    /** Dormindo, a IA não roda: ela só cai e espera (acorda ao ser ferida ou ao amanhecer). */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isDormant();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || tickCount % 10 != 0) return;
        boolean sleep = !level().isDay() && getTarget() == null && getLastHurtByMob() == null;
        if (sleep != isDormant()) {
            entityData.set(DORMANT, sleep);
            setNoGravity(!sleep);
            if (sleep) {
                getNavigation().stop();
                setXxa(0);
                setYya(0);
                setZza(0);
                playSoundHere(SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.4F);
            } else {
                playSoundHere(SoundEvents.BEACON_ACTIVATE, 0.6F, 1.6F);
                if (level() instanceof ServerLevel sl) ElementFx.burst(sl, position().add(0, 1, 0), Element.LIGHT, 14, 0.5);
            }
        }
    }

    private Vec3 core() {
        return position().add(0, 0.85, 0);
    }

    /** Carga: luz convergindo no núcleo e uma linha fina de mira até o alvo. */
    private void chargeRay(LivingEntity target, int left) {
        if (!(level() instanceof ServerLevel sl)) return;
        MobMagic.converge(sl, core(), ParticleTypes.END_ROD, 1.8, 2);
        if (left % 6 == 0) MobMagic.line(sl, core(), MobMagic.center(target), ElementFx.dust(Element.LIGHT, 0.6F), 0.7);
        if (left == 30) playSoundHere(SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.5F);
    }

    /** Raio solar: acerta a primeira criatura na linha (paredes bloqueiam). */
    private void solarRay(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl)) return;
        MobMagic.aim(this, target);
        Vec3 from = core();
        Vec3 dir = MobMagic.center(target).subtract(from).normalize();
        Vec3 far = from.add(dir.scale(24));
        HitResult wall = sl.clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = wall.getType() == HitResult.Type.MISS ? far : wall.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(sl, this, from, end, new AABB(from, end).inflate(1.0),
                e -> e.isPickable() && !e.isSpectator() && e != this && !isAlliedTo(e));
        if (hit != null) end = hit.getLocation();
        MobMagic.line(sl, from, end, ParticleTypes.END_ROD, 0.4);
        ElementFx.line(sl, from, end, Element.LIGHT);
        sl.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        playSoundHere(SoundEvents.BLAZE_SHOOT, 1.0F, 1.7F);
        playSoundHere(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.8F);
        if (hit != null) {
            Entity e = hit.getEntity();
            if (e.hurt(damageSources().indirectMagic(this, this), 6.0F) && e instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), this);
                if (!le.isInWaterRainOrBubble()) le.igniteForSeconds(3.0F);
            }
        }
    }

    /** Explosão solar de perto: queima e empurra. */
    private void solarFlare() {
        if (!(level() instanceof ServerLevel sl)) return;
        for (LivingEntity e : MobMagic.victimsAround(this, core(), 3.5)) {
            if (e.hurt(damageSources().indirectMagic(this, this), 5.0F)) {
                e.igniteForSeconds(2.0F);
                MobMagic.knockAway(e, position(), 0.9, 0.3);
            }
        }
        ElementFx.burst(sl, core(), Element.LIGHT, 30, 0.8);
        MobMagic.ring(sl, position(), ParticleTypes.FLAME, 2.5, 24, 0.05);
        playSoundHere(SoundEvents.FIRECHARGE_USE, 1.0F, 1.2F);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && isDormant() && !level().isClientSide) {
            entityData.set(DORMANT, false);
            setNoGravity(true);
        }
        return r;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    protected void clientEffects() {
        if (isDormant()) {
            if (random.nextInt(30) == 0) bodyParticle(ElementFx.dust(Element.LIGHT, 0.5F), 0.4, 0.3, 0);
            return;
        }
        if (random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.END_ROD, getRandomX(0.7), getY() + 0.4 + random.nextDouble(), getRandomZ(0.7), 0, 0.03, 0);
        }
        if (random.nextInt(10) == 0) bodyParticle(ParticleTypes.SMALL_FLAME, 0.2, 0.2, -0.02);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isDormant() ? null : SoundEvents.BEACON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_DAMAGE;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BEACON_DEACTIVATE;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.4F;
    }
}
