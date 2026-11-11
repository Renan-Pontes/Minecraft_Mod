package com.renanpontes.isekaimahou.feature.creatures_c.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.creatures_c.CcFx;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Gárgula: de dia — ou sempre que um jogador estiver OLHANDO para ela — é uma estátua de pedra imóvel e
 * invulnerável (se estiver no ar, despenca). À noite, sem ninguém olhando, acorda e caça voando.
 * Pedra: só picareta, explosão ou magia de TERRA a ferem como estátua; acordada resiste a dano físico (½)
 * e é fraca a terra (×2).
 */
public class Gargoyle extends Monster implements FlyingAnimal, GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_STATUE = SynchedEntityData.defineId(Gargoyle.class, EntityDataSerializers.BOOLEAN);
    private static final double WATCH_RANGE = 40.0;
    /** cos(~35°): o cone da "visão" do jogador que congela a gárgula. */
    private static final double WATCH_CONE = 0.82;

    private static final RawAnimation STATUE = RawAnimation.begin().thenLoop("animation.gargoyle.statue");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.gargoyle.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.gargoyle.fly");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.gargoyle.attack");
    private static final RawAnimation AWAKEN = RawAnimation.begin().thenPlay("animation.gargoyle.awaken");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Map<UUID, Long> hintTimes = new HashMap<>();
    private int unwatchedTicks;
    private long lastRoar = Long.MIN_VALUE / 2;

    public Gargoyle(EntityType<? extends Gargoyle> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 15, true);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 45.0)
                // Voo estilo Allay: velocidade final ≈ 10 × (FLYING_SPEED × modificador)² b/t → ~0,38 b/t caçando
                .add(Attributes.FLYING_SPEED, 0.15)
                .add(Attributes.MOVEMENT_SPEED, 0.15)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATUE, true);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.3, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.9));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    public boolean isStatue() {
        return entityData.get(DATA_STATUE);
    }

    private void setStatue(boolean statue) {
        entityData.set(DATA_STATUE, statue);
    }

    // =====================================================================================
    //  Estátua <-> acordada
    // =====================================================================================

    @Override
    protected boolean isImmobile() {
        return isStatue() || super.isImmobile();
    }

    @Override
    public boolean isPushable() {
        return !isStatue() && super.isPushable();
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (!isStatue()) super.knockback(strength, x, z);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel sl && isAlive()) {
            boolean freeze = sl.isDay() || isWatched(sl);
            if (freeze) {
                unwatchedTicks = 0;
                if (!isStatue()) petrify(sl);
            } else if (isStatue() && ++unwatchedTicks > 10) {
                awaken(sl);
            }
            if (isStatue()) {
                setNoGravity(false);
                Vec3 d = getDeltaMovement();
                setDeltaMovement(0.0, d.y, 0.0);
            }
        } else if (level().isClientSide && !isStatue() && tickCount % 4 == 0) {
            // Brasas nos olhos/rachaduras quando acordada
            level().addParticle(CcFx.EMBER_DUST, getRandomX(0.5), getY() + 1.6 + random.nextDouble() * 0.3, getRandomZ(0.5), 0, 0.01, 0);
        }
    }

    /** Algum jogador (com linha de visão) está olhando para ela? */
    private boolean isWatched(ServerLevel sl) {
        Vec3 center = getBoundingBox().getCenter();
        for (Player p : sl.players()) {
            if (p.isSpectator() || !p.isAlive() || p.distanceToSqr(this) > WATCH_RANGE * WATCH_RANGE) continue;
            if (p.hasEffect(MobEffects.BLINDNESS) || p.hasEffect(MobEffects.DARKNESS)) continue;
            Vec3 to = center.subtract(p.getEyePosition());
            double d = to.length();
            if (d < 0.01) return true;
            if (p.getViewVector(1.0F).dot(to.scale(1.0 / d)) > WATCH_CONE && p.hasLineOfSight(this)) return true;
        }
        return false;
    }

    private void petrify(ServerLevel sl) {
        setStatue(true);
        getNavigation().stop();
        setNoGravity(false);
        setZza(0.0F);
        setYya(0.0F);
        CcFx.stoneBurst(sl, this, 16);
        playSound(SoundEvents.DEEPSLATE_PLACE, 1.2F, 0.6F);
    }

    private void awaken(ServerLevel sl) {
        setStatue(false);
        unwatchedTicks = 0;
        triggerAnim("action", "awaken");
        CcFx.stoneBurst(sl, this, 24);
        sl.sendParticles(CcFx.EMBER_DUST, getX(), getY() + 1.6, getZ(), 10, 0.3, 0.2, 0.3, 0.0);
        playSound(SoundEvents.DEEPSLATE_BREAK, 1.3F, 0.6F);
        // O rugido só no primeiro despertar da caçada (no "olhou/desviou" da luta ficaria repetitivo)
        long now = sl.getGameTime();
        if (now - lastRoar > 200) {
            lastRoar = now;
            playSound(SoundEvents.RAVAGER_ROAR, 0.6F, 1.6F);
        }
    }

    // =====================================================================================
    //  Dano: pedra
    // =====================================================================================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        float mult;
        if (source.is(CoreRegistries.SPELL_DAMAGE)) {
            // Magia: como estátua, só terra (rachando a pedra) faz efeito de verdade
            Element el = ElementReactions.lastElement(this, level().getGameTime(), 1);
            mult = isStatue() && (el == null || !el.matches(Element.EARTH)) ? 0.25F : 1.0F;
        } else if (isPickaxeHit(source)) {
            mult = 1.5F;
        } else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            mult = 1.0F;
        } else {
            mult = isStatue() ? 0.0F : 0.5F;
        }
        if (mult <= 0.0F) {
            // Feedback só para golpes de verdade (sufocar/afogar dariam faísca e som a cada tick)
            if (!level().isClientSide && (source.getEntity() != null || source.getDirectEntity() != null)) clink(source.getEntity());
            return false;
        }
        return super.hurt(source, amount * mult);
    }

    private static boolean isPickaxeHit(DamageSource source) {
        Entity direct = source.getDirectEntity();
        return direct instanceof LivingEntity le && direct == source.getEntity() && le.getMainHandItem().is(ItemTags.PICKAXES);
    }

    /** Golpe que não fere a estátua: faíscas, som de pedra e uma dica (de vez em quando) para o jogador. */
    private void clink(@Nullable Entity attacker) {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.6, getZ(), 6, 0.3, 0.3, 0.3, 0.2);
            sl.sendParticles(CcFx.stoneChips(), getX(), getY() + getBbHeight() * 0.6, getZ(), 6, 0.25, 0.3, 0.25, 0.05);
        }
        playSound(SoundEvents.STONE_HIT, 1.0F, 1.4F);
        if (attacker instanceof Player p) {
            long now = level().getGameTime();
            Long last = hintTimes.get(p.getUUID());
            if (last == null || now - last > 200) {
                hintTimes.put(p.getUUID(), now);
                p.displayClientMessage(Component.translatable("message.isekai_mahou.gargoyle_stone"), true);
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            triggerAnim("action", "attack");
            playSound(SoundEvents.RAVAGER_ATTACK, 0.8F, 1.4F);
        }
        return hit;
    }

    // =====================================================================================
    //  Voo / queda
    // =====================================================================================

    @Override
    public void travel(Vec3 input) {
        if (isStatue() || hasEffect(CoreRegistries.STUNNED)) {
            // Pedra (ou gárgula atordoada, que não bate as asas) cai como pedra
            if (!level().isClientSide) setNoGravity(false);
            super.travel(Vec3.ZERO);
            return;
        }
        if (isControlledByLocalInstance()) {
            if (isInWater()) {
                moveRelative(0.02F, input);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.8));
            } else {
                moveRelative(getSpeed(), input);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.91));
            }
        }
        calculateEntityAnimation(false);
    }

    @Override
    public boolean isFlying() {
        return !onGround() && !isStatue();
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (fallDistance > 2.0F && level() instanceof ServerLevel sl) {
            // A estátua despencando: baque de pedra, sem dano
            CcFx.stoneBurst(sl, this, 12);
            playSound(SoundEvents.DEEPSLATE_FALL, 1.5F, 0.6F);
        }
        return false;
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return isStatue() ? null : SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return isStatue() ? SoundEvents.DEEPSLATE_HIT : SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.DEEPSLATE_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.STONE_STEP, 0.5F, 0.8F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.4F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && isFlying() && tickCount % 20 == 0) {
            level().playLocalSound(getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP, getSoundSource(), 0.25F,
                    1.5F + random.nextFloat() * 0.2F, false);
        }
    }

    // =====================================================================================
    //  Save / animações
    // =====================================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Statue", isStatue());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Statue")) setStatue(tag.getBoolean("Statue"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            if (isStatue()) return state.setAndContinue(STATUE);
            return state.setAndContinue(state.isMoving() || Mth.abs((float) getDeltaMovement().y) > 0.05F ? FLY : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("awaken", AWAKEN));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
