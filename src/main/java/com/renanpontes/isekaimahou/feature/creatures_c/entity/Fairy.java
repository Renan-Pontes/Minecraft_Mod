package com.renanpontes.isekaimahou.feature.creatures_c.entity;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.creatures_c.CcFx;
import com.renanpontes.isekaimahou.feature.creatures_c.CcRegistries;

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
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * Fada: criaturinha pacífica dos campos floridos. Voa, brilha, visita flores e de vez em quando cura
 * jogadores feridos por perto. Quem lhe der flores pode virar seu AMIGO (1 em 3 por flor): ela passa a
 * seguir o amigo, cura mais e mais forte e às vezes traz Pó de Fada de presente.
 * Amigo agachado com a mão vazia: manda ficar/seguir. Foge de quem a machuca.
 */
public class Fairy extends PathfinderMob implements FlyingAnimal, GeoEntity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_FRIEND = SynchedEntityData.defineId(Fairy.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_STAYING = SynchedEntityData.defineId(Fairy.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.fairy.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.fairy.fly");
    private static final RawAnimation HEAL = RawAnimation.begin().thenPlay("animation.fairy.heal");
    private static final RawAnimation HAPPY = RawAnimation.begin().thenPlay("animation.fairy.happy");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int healCooldown = 100;
    private int giftCooldown = 6000;
    /** Quem machucou a fada fica sem cura dela por 2 minutos. */
    @Nullable
    private UUID grudge;
    private int grudgeTicks;

    public Fairy(EntityType<? extends Fairy> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.xpReward = 3;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                // Voo estilo Allay: velocidade final ≈ 10 × (FLYING_SPEED × modificador)² b/t
                // (0.17 → passeio ~0,2 b/t, seguindo o amigo ~0,5 b/t)
                .add(Attributes.FLYING_SPEED, 0.17)
                .add(Attributes.MOVEMENT_SPEED, 0.17)
                .add(Attributes.FOLLOW_RANGE, 24.0);
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
        builder.define(DATA_FRIEND, Optional.empty());
        builder.define(DATA_STAYING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new FleeGoal());
        goalSelector.addGoal(2, new StayGoal());
        goalSelector.addGoal(3, new FollowFriendGoal());
        goalSelector.addGoal(4, new VisitFlowersGoal());
        goalSelector.addGoal(5, new WaterAvoidingRandomFlyingGoal(this, 0.9));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    // =====================================================================================
    //  Amizade
    // =====================================================================================

    @Nullable
    public UUID getFriendId() {
        return entityData.get(DATA_FRIEND).orElse(null);
    }

    public boolean hasFriend() {
        return entityData.get(DATA_FRIEND).isPresent();
    }

    public boolean isFriend(Entity e) {
        return e != null && e.getUUID().equals(getFriendId());
    }

    @Nullable
    public Player getFriend() {
        UUID id = getFriendId();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    public boolean isStaying() {
        return entityData.get(DATA_STAYING);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        return isFriend(other) || super.isAlliedTo(other);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean client = level().isClientSide;
        if (stack.is(ItemTags.FLOWERS)) {
            if (!client) {
                stack.consume(1, player);
                if (isFriend(player)) {
                    // Agrado para a amiga: cura e alegria
                    heal(4.0F);
                    celebrate();
                } else if (!hasFriend() && random.nextInt(3) == 0) {
                    entityData.set(DATA_FRIEND, Optional.of(player.getUUID()));
                    setPersistenceRequired();
                    celebrate();
                    player.displayClientMessage(Component.translatable("message.isekai_mahou.fairy_friend"), true);
                } else {
                    if (level() instanceof ServerLevel sl) {
                        sl.sendParticles(CcFx.FAIRY_PINK, getX(), getY() + 0.5, getZ(), 8, 0.3, 0.3, 0.3, 0.0);
                    }
                    playSound(SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 0.8F, 1.6F);
                }
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (isFriend(player) && stack.isEmpty() && player.isSecondaryUseActive()) {
            if (!client) {
                boolean stay = !isStaying();
                entityData.set(DATA_STAYING, stay);
                getNavigation().stop();
                player.displayClientMessage(Component.translatable(stay ? "message.isekai_mahou.fairy_stay"
                        : "message.isekai_mahou.fairy_follow"), true);
                playSound(SoundEvents.ALLAY_ITEM_TAKEN, 0.8F, 1.5F);
            }
            return InteractionResult.sidedSuccess(client);
        }
        return super.mobInteract(player, hand);
    }

    private void celebrate() {
        triggerAnim("action", "happy");
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, getX(), getY() + 0.9, getZ(), 4, 0.3, 0.2, 0.3, 0.0);
            sl.sendParticles(CcFx.FAIRY_MINT, getX(), getY() + 0.5, getZ(), 12, 0.4, 0.4, 0.4, 0.0);
        }
        playSound(SoundEvents.ALLAY_ITEM_GIVEN, 1.0F, 1.4F);
    }

    // =====================================================================================
    //  Cura e presentes
    // =====================================================================================

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel sl && isAlive()) {
            if (grudgeTicks > 0) grudgeTicks--;
            if (--healCooldown <= 0) tryHeal(sl);
            Player friend = getFriend();
            if (friend != null && --giftCooldown <= 0 && distanceToSqr(friend) < 64.0) {
                giftCooldown = 6000 + random.nextInt(3000);
                spawnAtLocation(CcRegistries.FAIRY_DUST.get());
                sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.4, getZ(), 6, 0.2, 0.2, 0.2, 0.02);
                playSound(SoundEvents.ALLAY_ITEM_GIVEN, 1.0F, 1.2F);
            }
        } else if (level().isClientSide && tickCount % 3 == 0) {
            // Rastro de brilho
            level().addParticle(random.nextBoolean() ? CcFx.FAIRY_PINK : CcFx.FAIRY_MINT,
                    getRandomX(0.6), getY() + 0.2 + random.nextDouble() * 0.5, getRandomZ(0.6), 0, -0.01, 0);
            if (random.nextInt(12) == 0) {
                level().addParticle(ParticleTypes.END_ROD, getRandomX(0.4), getY() + 0.5, getRandomZ(0.4), 0, 0.005, 0);
            }
        }
    }

    private void tryHeal(ServerLevel sl) {
        Player friend = getFriend();
        Player best = null;
        for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(6.0),
                pl -> pl.isAlive() && !pl.isSpectator() && pl.getHealth() < pl.getMaxHealth())) {
            if (grudgeTicks > 0 && p.getUUID().equals(grudge)) continue;
            if (p == friend) {
                best = p;
                break;
            }
            if (best == null || p.getHealth() < best.getHealth()) best = p;
        }
        if (best == null) {
            healCooldown = 40;
            return;
        }
        boolean isFriend = best == friend;
        best.heal(isFriend ? 4.0F : 2.0F);
        triggerAnim("action", "heal");
        sl.sendParticles(ParticleTypes.HEART, best.getX(), best.getY() + best.getBbHeight() + 0.3, best.getZ(), isFriend ? 3 : 2, 0.3, 0.1, 0.3, 0.0);
        sl.sendParticles(CcFx.FAIRY_PINK, best.getX(), best.getY() + 1.0, best.getZ(), 14, 0.4, 0.6, 0.4, 0.0);
        sl.sendParticles(CcFx.FAIRY_MINT, getX(), getY() + 0.5, getZ(), 8, 0.2, 0.2, 0.2, 0.0);
        sl.playSound(null, best.getX(), best.getY(), best.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.8F);
        healCooldown = isFriend ? 140 + random.nextInt(60) : 260 + random.nextInt(120);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // A amiga não se machuca com golpes acidentais do próprio amigo
        if (isFriend(source.getEntity()) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        boolean hurt = super.hurt(source, amount);
        if (hurt && source.getEntity() instanceof Player p) {
            grudge = p.getUUID();
            grudgeTicks = 2400;
        }
        return hurt;
    }

    // =====================================================================================
    //  Voo
    // =====================================================================================

    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance()) {
            if (hasEffect(CoreRegistries.STUNNED)) {
                // Atordoada: as asas param e ela desce flutuando devagar
                input = Vec3.ZERO;
                setDeltaMovement(getDeltaMovement().add(0.0, -0.02, 0.0));
            }
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
        return !onGround();
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
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
        return super.getVoicePitch() * 1.25F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    // =====================================================================================
    //  Save / animações
    // =====================================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID friend = getFriendId();
        if (friend != null) tag.putUUID("Friend", friend);
        tag.putBoolean("Staying", isStaying());
        tag.putInt("GiftCooldown", giftCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_FRIEND, tag.hasUUID("Friend") ? Optional.of(tag.getUUID("Friend")) : Optional.empty());
        entityData.set(DATA_STAYING, tag.getBoolean("Staying"));
        if (tag.contains("GiftCooldown")) giftCooldown = tag.getInt("GiftCooldown");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4,
                state -> state.setAndContinue(state.isMoving() ? FLY : IDLE)));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("heal", HEAL)
                .triggerableAnim("happy", HAPPY));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // =====================================================================================
    //  IA
    // =====================================================================================

    /** Machucada: voa para longe de quem bateu, rápido e para cima. */
    class FleeGoal extends Goal {
        FleeGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity attacker = getLastHurtByMob();
            return attacker != null && attacker.isAlive() && tickCount - getLastHurtByMobTimestamp() < 100;
        }

        @Override
        public void tick() {
            LivingEntity attacker = getLastHurtByMob();
            if (attacker == null) return;
            Vec3 away = position().subtract(attacker.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : away.normalize();
            Vec3 to = position().add(away.scale(6.0)).add(0, 2.5, 0);
            getMoveControl().setWantedPosition(to.x, to.y, to.z, 1.5);
        }
    }

    /** Amigo mandou ficar: paira no lugar. */
    class StayGoal extends Goal {
        StayGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return isStaying() && hasFriend();
        }

        @Override
        public void start() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            setDeltaMovement(getDeltaMovement().scale(0.6));
        }
    }

    /** Segue o amigo; se ele se afastar demais, aparece do lado dele num brilho. */
    class FollowFriendGoal extends Goal {
        private int recalc;

        FollowFriendGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            Player f = getFriend();
            return f != null && !isStaying() && !f.isSpectator() && distanceToSqr(f) > 16.0;
        }

        @Override
        public boolean canContinueToUse() {
            Player f = getFriend();
            return f != null && !isStaying() && distanceToSqr(f) > 6.0 && !getNavigation().isDone();
        }

        @Override
        public void start() {
            recalc = 0;
        }

        @Override
        public void tick() {
            Player f = getFriend();
            if (f == null) return;
            getLookControl().setLookAt(f, 10.0F, 30.0F);
            if (--recalc > 0) return;
            recalc = 10;
            if (distanceToSqr(f) > 24.0 * 24.0) {
                teleportNear(f);
            } else {
                getNavigation().moveTo(f.getX(), f.getY() + 1.6, f.getZ(), 1.3);
            }
        }

        private void teleportNear(Player f) {
            for (int i = 0; i < 10; i++) {
                double x = f.getX() + (random.nextDouble() - 0.5) * 4.0;
                double y = f.getY() + 1.0 + random.nextDouble() * 1.5;
                double z = f.getZ() + (random.nextDouble() - 0.5) * 4.0;
                if (level().noCollision(Fairy.this, getType().getSpawnAABB(x, y, z))) {
                    moveTo(x, y, z, getYRot(), getXRot());
                    getNavigation().stop();
                    if (level() instanceof ServerLevel sl) {
                        sl.sendParticles(CcFx.FAIRY_PINK, x, y + 0.4, z, 12, 0.3, 0.3, 0.3, 0.0);
                    }
                    return;
                }
            }
        }
    }

    /** Visita flores próximas e paira sobre elas um pouco. */
    class VisitFlowersGoal extends Goal {
        private BlockPos flower;
        private int timer;

        VisitFlowersGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (random.nextInt(60) != 0) return false;
            flower = findFlower();
            return flower != null;
        }

        @Override
        public boolean canContinueToUse() {
            return flower != null && timer > 0 && level().getBlockState(flower).is(BlockTags.FLOWERS);
        }

        @Override
        public void start() {
            timer = 120 + random.nextInt(80);
        }

        @Override
        public void stop() {
            flower = null;
        }

        @Override
        public void tick() {
            timer--;
            double y = flower.getY() + 1.1 + Math.sin(tickCount * 0.15) * 0.25;
            getMoveControl().setWantedPosition(flower.getX() + 0.5, y, flower.getZ() + 0.5, 0.8);
            if (timer % 20 == 0 && level() instanceof ServerLevel sl) {
                sl.sendParticles(CcFx.FAIRY_MINT, flower.getX() + 0.5, flower.getY() + 0.6, flower.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.0);
            }
        }

        @Nullable
        private BlockPos findFlower() {
            BlockPos base = blockPosition();
            for (int i = 0; i < 20; i++) {
                BlockPos p = base.offset(random.nextInt(17) - 8, random.nextInt(7) - 4, random.nextInt(17) - 8);
                if (level().getBlockState(p).is(BlockTags.FLOWERS)) return p;
            }
            return null;
        }
    }
}
