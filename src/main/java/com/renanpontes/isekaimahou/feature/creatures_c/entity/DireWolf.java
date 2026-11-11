package com.renanpontes.isekaimahou.feature.creatures_c.entity;

import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.creatures_c.CcRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Lobo Atroz: lobo gigante das taigas. Neutro (só ataca se provocado), caça ovelhas/coelhos/cabras.
 * Domado com carne (1 em 4); domado e com sela vira MONTARIA terrestre com pulo carregado bem alto
 * (barra de pulo igual à do cavalo) e morde o que o cavaleiro atacar. Tesoura tira a sela.
 */
public class DireWolf extends TamableAnimal implements NeutralMob, PlayerRideableJumping, Saddleable, GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_SADDLED = SynchedEntityData.defineId(DireWolf.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ANGER = SynchedEntityData.defineId(DireWolf.class, EntityDataSerializers.INT);
    private static final UniformInt ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    /** Velocidade vertical do pulo da montaria com a barra cheia (~5 blocos de altura). */
    private static final double RIDER_JUMP = 0.92;

    private static final Predicate<LivingEntity> PREY = e -> {
        EntityType<?> t = e.getType();
        return t == EntityType.SHEEP || t == EntityType.RABBIT || t == EntityType.FOX || t == EntityType.GOAT || t == EntityType.PIG;
    };

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.dire_wolf.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.dire_wolf.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.dire_wolf.run");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.dire_wolf.sit");
    private static final RawAnimation JUMP = RawAnimation.begin().thenLoop("animation.dire_wolf.jump");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("animation.dire_wolf.bite");
    private static final RawAnimation HOWL = RawAnimation.begin().thenPlay("animation.dire_wolf.howl");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID angerTarget;
    private float jumpPending;
    private int riderBiteCooldown;

    public DireWolf(EntityType<? extends DireWolf> type, Level level) {
        super(type, level);
        this.setTame(false, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ARMOR, 3.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.SAFE_FALL_DISTANCE, 5.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SADDLED, false);
        builder.define(DATA_ANGER, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.45F) {
            @Override
            public boolean canUse() {
                return !DireWolf.this.isVehicle() && super.canUse();
            }
        });
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.25, true) {
            @Override
            public boolean canUse() {
                return !DireWolf.this.isVehicle() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return !DireWolf.this.isVehicle() && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.1, 10.0F, 3.0F));
        goalSelector.addGoal(6, new BreedGoal(this, 1.0));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.9) {
            @Override
            public boolean canUse() {
                return !DireWolf.this.isVehicle() && super.canUse();
            }
        });
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        targetSelector.addGoal(5, new NonTameRandomTargetGoal<>(this, Animal.class, false, PREY));
        targetSelector.addGoal(6, new NearestAttackableTargetGoal<>(this, AbstractSkeleton.class, false));
        targetSelector.addGoal(8, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    // =====================================================================================
    //  Doma, dono e interação
    // =====================================================================================

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(ItemTags.MEAT);
    }

    @Override
    protected void applyTamingSideEffects() {
        // Domado fica mais forte e resistente (vira companheiro de batalha)
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        if (isTame()) {
            if (hp != null) hp.setBaseValue(60.0);
            if (dmg != null) dmg.setBaseValue(8.0);
            setHealth(getMaxHealth());
        } else {
            if (hp != null) hp.setBaseValue(40.0);
            if (dmg != null) dmg.setBaseValue(7.0);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean client = level().isClientSide;

        if (!isTame()) {
            // Selvagem: carne = tentativa de doma; bravo, ele rosna e recusa (sem gastar a carne)
            if (isFood(stack)) {
                if (!client) {
                    if (isAngry()) {
                        playSound(SoundEvents.WOLF_GROWL, 1.0F, 0.6F);
                    } else {
                        stack.consume(1, player);
                        tryToTame(player);
                    }
                }
                return InteractionResult.sidedSuccess(client);
            }
            return super.mobInteract(player, hand);
        }

        if (!isOwnedBy(player)) {
            // Só o dono sela; os outros podem no máximo alimentar para cruzar
            if (stack.is(Items.SADDLE)) return InteractionResult.CONSUME;
            return super.mobInteract(player, hand);
        }

        if (isFood(stack) && getHealth() < getMaxHealth()) {
            if (!client) {
                var food = stack.getFoodProperties(this);
                heal(food != null ? food.nutrition() * 2.0F : 4.0F);
                stack.consume(1, player);
                level().broadcastEntityEvent(this, (byte) 7);
                playSound(SoundEvents.GENERIC_EAT, 0.8F, 0.8F);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (stack.is(Items.SADDLE) && isSaddleable() && !isSaddled()) {
            if (!client) {
                equipSaddle(stack.copyWithCount(1), SoundSource.NEUTRAL);
                stack.consume(1, player);
                player.displayClientMessage(Component.translatable("message.isekai_mahou.dire_wolf_saddled"), true);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (stack.is(Items.SHEARS) && isSaddled()) {
            if (!client) {
                setSaddled(false);
                spawnAtLocation(Items.SADDLE);
                playSound(SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
                stack.hurtAndBreak(1, player, getSlotForHand(hand));
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (isFood(stack)) {
            InteractionResult breed = super.mobInteract(player, hand);
            if (breed.consumesAction()) return breed;
        }
        if (!player.isSecondaryUseActive() && isSaddled() && !isBaby() && !isVehicle()) {
            if (!client) {
                setOrderedToSit(false);
                setInSittingPose(false);
                navigation.stop();
                setTarget(null);
                player.setYRot(getYRot());
                player.setXRot(getXRot());
                player.startRiding(this);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (!client) {
            boolean sit = !isOrderedToSit();
            setOrderedToSit(sit);
            jumping = false;
            navigation.stop();
            setTarget(null);
            player.displayClientMessage(Component.translatable(sit ? "message.isekai_mahou.dire_wolf_sit"
                    : "message.isekai_mahou.dire_wolf_follow"), true);
        }
        return InteractionResult.sidedSuccess(client);
    }

    private void tryToTame(Player player) {
        if (random.nextInt(4) == 0 && !EventHooks.onAnimalTame(this, player)) {
            tame(player);
            navigation.stop();
            setTarget(null);
            setOrderedToSit(true);
            level().broadcastEntityEvent(this, (byte) 7);
            playSound(SoundEvents.WOLF_AMBIENT, 1.0F, 0.7F);
            player.displayClientMessage(Component.translatable("message.isekai_mahou.dire_wolf_tamed"), true);
        } else {
            level().broadcastEntityEvent(this, (byte) 6);
            playSound(SoundEvents.WOLF_GROWL, 0.8F, 0.75F);
        }
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof Creeper || target instanceof Ghast || target instanceof ArmorStand) return false;
        if (target instanceof TamableAnimal t) return !t.isTame() || t.getOwner() != owner;
        if (target instanceof Player p && owner instanceof Player o && !o.canHarmPlayer(p)) return false;
        return !(target instanceof AbstractHorse h && h.isTamed());
    }

    @Override
    public boolean canMate(Animal other) {
        return other != this && other instanceof DireWolf w && isTame() && w.isTame()
                && !isInSittingPose() && !w.isInSittingPose() && isInLove() && w.isInLove();
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        DireWolf pup = CcRegistries.DIRE_WOLF.get().create(level);
        if (pup != null && getOwnerUUID() != null) {
            pup.setOwnerUUID(getOwnerUUID());
            pup.setTame(true, true);
        }
        return pup;
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 4;
    }

    // =====================================================================================
    //  Sela e montaria
    // =====================================================================================

    @Override
    public boolean isSaddleable() {
        return isAlive() && isTame() && !isBaby();
    }

    @Override
    public void equipSaddle(ItemStack stack, @Nullable SoundSource source) {
        setSaddled(true);
        if (source != null) level().playSound(null, this, SoundEvents.HORSE_SADDLE, source, 0.6F, 0.9F);
    }

    @Override
    public boolean isSaddled() {
        return entityData.get(DATA_SADDLED);
    }

    public void setSaddled(boolean saddled) {
        entityData.set(DATA_SADDLED, saddled);
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (isSaddled()) spawnAtLocation(Items.SADDLE);
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        if (isSaddled() && getFirstPassenger() instanceof Player p) return p;
        return super.getControllingPassenger();
    }

    @Override
    protected void tickRidden(Player player, Vec3 travel) {
        super.tickRidden(player, travel);
        setRot(player.getYRot(), player.getXRot() * 0.5F);
        yRotO = yBodyRot = yHeadRot = getYRot();
        if (isControlledByLocalInstance() && onGround()) {
            if (jumpPending > 0.0F) executeRidersJump(jumpPending, travel);
            jumpPending = 0.0F;
        }
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travel) {
        float strafe = player.xxa * 0.5F;
        float forward = player.zza;
        if (forward <= 0.0F) forward *= 0.3F;
        return new Vec3(strafe, 0.0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    /** Pulo carregado: sobe alto e, se estiver indo para frente, dá um bote. */
    private void executeRidersJump(float scale, Vec3 travel) {
        double v = RIDER_JUMP * scale * getBlockJumpFactor() + getJumpBoostPower();
        Vec3 d = getDeltaMovement();
        setDeltaMovement(d.x, v, d.z);
        hasImpulse = true;
        CommonHooks.onLivingJump(this);
        if (travel.z > 0.0) {
            float sin = Mth.sin(getYRot() * Mth.DEG_TO_RAD);
            float cos = Mth.cos(getYRot() * Mth.DEG_TO_RAD);
            setDeltaMovement(getDeltaMovement().add(-0.5F * sin * scale, 0.0, 0.5F * cos * scale));
        }
    }

    @Override
    public void onPlayerJump(int power) {
        if (!isSaddled()) return;
        if (power < 0) power = 0;
        jumpPending = power >= 90 ? 1.0F : 0.4F + 0.6F * power / 90.0F;
    }

    @Override
    public boolean canJump() {
        return isSaddled();
    }

    @Override
    public void handleStartJump(int power) {
        playSound(SoundEvents.GOAT_LONG_JUMP, 0.9F, 0.75F);
    }

    @Override
    public void handleStopJump() {
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (passenger instanceof LivingEntity le) le.yBodyRot = yBodyRot;
    }

    /** Ao desmontar, o cavaleiro desce ao lado do lobo (como no porco/cavalo), não em cima dele. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        Direction dir = getMotionDirection();
        if (dir.getAxis() != Direction.Axis.Y) {
            BlockPos base = blockPosition();
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (Pose pose : passenger.getDismountPoses()) {
                AABB box = passenger.getLocalBoundsForPose(pose);
                for (int[] off : DismountHelper.offsetsForDirection(dir)) {
                    m.set(base.getX() + off[0], base.getY(), base.getZ() + off[1]);
                    double floor = level().getBlockFloorHeight(m);
                    if (!DismountHelper.isBlockFloorValid(floor)) continue;
                    Vec3 at = Vec3.upFromBottomCenterOf(m, floor);
                    if (DismountHelper.canDismountTo(level(), passenger, box.move(at))) {
                        passenger.setPose(pose);
                        return at;
                    }
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    /** A montaria amortece quedas: lobo e cavaleiro só se machucam em quedas bem altas (pulo alto). */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return super.causeFallDamage(Math.max(0.0F, fallDistance - 4.0F), multiplier * 0.6F, source);
    }

    // =====================================================================================
    //  Combate
    // =====================================================================================

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            triggerAnim("action", "bite");
            playSound(SoundEvents.WOLF_GROWL, 0.9F, 0.65F);
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (!level().isClientSide) setOrderedToSit(false);
        return super.hurt(source, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel sl) {
            updatePersistentAnger(sl, true);
            if (riderBiteCooldown > 0) riderBiteCooldown--;
            if (getControllingPassenger() instanceof Player rider) riderAssist(rider);
            else if (!isInSittingPose() && getTarget() == null && sl.isNight() && random.nextInt(1600) == 0) howl();
        }
    }

    /** Montado: morde o último inimigo que o cavaleiro acertou, se estiver ao alcance. */
    private void riderAssist(Player rider) {
        LivingEntity t = rider.getLastHurtMob();
        if (t == null || t == this || !t.isAlive() || riderBiteCooldown > 0 || isAlliedTo(t) || !wantsToAttack(t, rider)) return;
        if (rider.tickCount - rider.getLastHurtMobTimestamp() > 60) return;
        if (distanceToSqr(t) > 9.0) return;
        riderBiteCooldown = 20;
        doHurtTarget(t);
    }

    private void howl() {
        triggerAnim("action", "howl");
        playSound(SoundEvents.WOLF_HOWL, 2.0F, 0.7F);
    }

    // =====================================================================================
    //  Raiva (neutro)
    // =====================================================================================

    @Override
    public int getRemainingPersistentAngerTime() {
        return entityData.get(DATA_ANGER);
    }

    @Override
    public void setRemainingPersistentAngerTime(int time) {
        entityData.set(DATA_ANGER, time);
    }

    @Override
    public void startPersistentAngerTimer() {
        setRemainingPersistentAngerTime(ANGER_TIME.sample(random));
    }

    @Override
    @Nullable
    public UUID getPersistentAngerTarget() {
        return angerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID target) {
        this.angerTarget = target;
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================

    @Override
    protected SoundEvent getAmbientSound() {
        if (isAngry()) return SoundEvents.WOLF_GROWL;
        if (isTame() && getHealth() < getMaxHealth() * 0.4F) return SoundEvents.WOLF_WHINE;
        return random.nextInt(3) == 0 ? SoundEvents.WOLF_PANT : SoundEvents.WOLF_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WOLF_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOLF_STEP, 0.3F, 0.7F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.72F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.1F;
    }

    // =====================================================================================
    //  Save
    // =====================================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Saddled", isSaddled());
        addPersistentAngerSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSaddled(tag.getBoolean("Saddled"));
        readPersistentAngerSaveData(level(), tag);
    }

    // =====================================================================================
    //  Animações (GeckoLib)
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isInSittingPose()) return state.setAndContinue(SIT);
            if (!onGround() && !isInWater() && Math.abs(getDeltaMovement().y) > 0.12) return state.setAndContinue(JUMP);
            if (state.isMoving()) return state.setAndContinue(state.getLimbSwingAmount() > 0.55F ? RUN : WALK);
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("bite", BITE)
                .triggerableAnim("howl", HOWL));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    /** Evento 7 (doma/cura): além dos corações do vanilla, folhinhas verdes. */
    @Override
    public void handleEntityEvent(byte id) {
        super.handleEntityEvent(id);
        if (id == 7 && level().isClientSide) {
            for (int i = 0; i < 6; i++) {
                level().addParticle(ParticleTypes.HAPPY_VILLAGER, getRandomX(1.0), getRandomY() + 0.5, getRandomZ(1.0), 0, 0.05, 0);
            }
        }
    }
}
