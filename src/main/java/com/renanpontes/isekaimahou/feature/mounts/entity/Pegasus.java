package com.renanpontes.isekaimahou.feature.mounts.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.mounts.entity.goal.AnswerCallGoal;
import com.renanpontes.isekaimahou.feature.mounts.entity.goal.PanicFlightGoal;
import com.renanpontes.isekaimahou.feature.mounts.entity.goal.SoarGoal;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RandomStandGoal;
import net.minecraft.world.entity.ai.goal.RunAroundLikeCrazyGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Pégaso: cavalo alado do Santuário Celestial. Domado COMO CAVALO (montando até ele aceitar), mas só deixa
 * alguém tentar depois de ganhar confiança — uma Maçã Dourada dá muita (a Encantada doma na hora).
 * Domado: sela pelo inventário de cavalo (agachar + clique), cruza com cenoura/maçã dourada.
 * Montado: galopa no chão; pulo decola e sobe, agachar desce. Habilidade "Galope Celestial".
 */
public class Pegasus extends AbstractHorse implements GeoEntity, FlyingMount {
    public static final String ID = "pegasus";
    /** Confiança mínima (temper) para aceitar alguém no lombo. */
    private static final int TRUST = 25;
    private static final MountFlight.Params PARAMS = new MountFlight.Params(0.82, 0.38, 0.5, 0.035, 0.085, 0.11, 0.6,
            0.65, false, 0.3, 14F, MountFlight.wingSound(), 1.55F, 13);

    private static final EntityDataAccessor<Long> DATA_ABILITY_READY = SynchedEntityData.defineId(Pegasus.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_ABILITY_END = SynchedEntityData.defineId(Pegasus.class, EntityDataSerializers.LONG);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.pegasus.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.pegasus.walk");
    private static final RawAnimation GALLOP = RawAnimation.begin().thenLoop("animation.pegasus.gallop");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.pegasus.fly");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.pegasus.glide");
    private static final RawAnimation GRAZE = RawAnimation.begin().thenLoop("animation.pegasus.graze");
    private static final RawAnimation REAR = RawAnimation.begin().thenLoop("animation.pegasus.rear");
    private static final RawAnimation BLESS = RawAnimation.begin().thenPlay("animation.pegasus.bless");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final MountFlight flight = new MountFlight(PARAMS);
    private final PathNavigation groundNavigation;
    private final FlyingPathNavigation airNavigation;
    private final MoveControl groundMoveControl;
    private final MountFlyMoveControl airMoveControl;
    private boolean flyingMode;
    private int flightIntent;
    @Nullable
    private UUID caller;
    private int callTicks;

    public Pegasus(EntityType<? extends Pegasus> type, Level level) {
        super(type, level);
        this.groundNavigation = this.navigation;
        this.groundMoveControl = this.moveControl;
        this.airNavigation = new FlyingPathNavigation(this, level);
        this.airNavigation.setCanOpenDoors(false);
        this.airNavigation.setCanFloat(true);
        this.airNavigation.setCanPassDoors(true);
        this.airMoveControl = new MountFlyMoveControl(this, 16F, false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseHorseAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.JUMP_STRENGTH, 0.8)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.ARMOR, 2.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ABILITY_READY, 0L);
        builder.define(DATA_ABILITY_END, 0L);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new AnswerCallGoal<>(this, 1.5));
        goalSelector.addGoal(1, new PanicFlightGoal<>(this, 1.4));
        goalSelector.addGoal(1, new RunAroundLikeCrazyGoal(this, 1.2));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0, Pegasus.class));
        goalSelector.addGoal(3, new TemptGoal(this, 1.25, s -> s.is(ItemTags.HORSE_TEMPT_ITEMS), false));
        goalSelector.addGoal(4, new FollowParentGoal(this, 1.0));
        goalSelector.addGoal(5, new SoarGoal<>(this, 900));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        goalSelector.addGoal(9, new RandomStandGoal(this));
    }

    @Override
    protected void randomizeAttributes(RandomSource random) {
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (hp != null) hp.setBaseValue(26.0 + random.nextInt(9));
        if (speed != null) speed.setBaseValue(0.27 + random.nextDouble() * 0.06);
    }

    // =====================================================================================
    //  Confiança e doma (como cavalo + Maçã Dourada)
    // =====================================================================================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        boolean client = level().isClientSide;
        boolean openInv = !isBaby() && isTamed() && player.isSecondaryUseActive();
        if (!isVehicle() && !openInv && !isBaby()) {
            ItemStack stack = player.getItemInHand(hand);
            if (!isTamed() && stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                if (!client && !EventHooks.onAnimalTame(this, player)) {
                    stack.consume(1, player);
                    tameWithName(player);
                    heal(getMaxHealth());
                }
                return InteractionResult.sidedSuccess(client);
            }
            if (isFood(stack)) return fedFood(player, stack);
            if (!isTamed() && !stack.isEmpty()) {
                makeMad();
                return InteractionResult.sidedSuccess(client);
            }
            if (!isTamed() && getTemper() < TRUST) {
                // Ainda não confia: empina, bate as asas e se afasta
                if (!client) {
                    makeMad();
                    setDeltaMovement(getDeltaMovement().add(0, 0.35, 0));
                    player.displayClientMessage(Component.translatable("message.isekai_mahou.pegasus_distrust"), true);
                }
                return InteractionResult.sidedSuccess(client);
            }
        }
        return super.mobInteract(player, hand);
    }

    @Override
    protected boolean handleEating(Player player, ItemStack stack) {
        boolean wild = !isTamed();
        boolean ate = super.handleEating(player, stack);
        if (wild && stack.is(Items.GOLDEN_APPLE) && !level().isClientSide) {
            modifyTemper(20); // além dos +10 do cavalo: o Pégaso ama maçãs douradas
            ate = true;
            MountFx.burst(this, ParticleTypes.END_ROD, 10);
            int pct = Math.min(100, getTemper() * 100 / getMaxTemper());
            player.displayClientMessage(Component.translatable("message.isekai_mahou.pegasus_trust", pct)
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        return ate;
    }

    @Override
    public boolean tameWithName(Player player) {
        boolean ok = super.tameWithName(player);
        if (ok && !level().isClientSide) MountFx.tamed(this, player, ParticleTypes.END_ROD);
        return ok;
    }

    @Override
    protected void doPlayerRide(Player player) {
        super.doPlayerRide(player);
        setFlyingMode(false);
        clearCall();
        if (!level().isClientSide && isTamed() && isSaddled() && player instanceof ServerPlayer sp && hasPassenger(sp)) {
            MountFx.controlsHint(sp, this);
        }
    }

    @Override
    public boolean canMate(Animal other) {
        return other != this && other instanceof Pegasus p && canParent() && p.canParent();
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        Entity e = getType().create(level);
        if (!(e instanceof Pegasus foal)) return null;
        AttributeInstance hp = foal.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance sp = foal.getAttribute(Attributes.MOVEMENT_SPEED);
        if (hp != null) hp.setBaseValue((getAttributeBaseValue(Attributes.MAX_HEALTH) + other.getAttributeBaseValue(Attributes.MAX_HEALTH)) / 2.0
                + random.nextInt(5) - 2);
        if (sp != null) sp.setBaseValue(Math.min(0.36, (getAttributeBaseValue(Attributes.MOVEMENT_SPEED)
                + other.getAttributeBaseValue(Attributes.MOVEMENT_SPEED)) / 2.0 + (random.nextDouble() - 0.4) * 0.02));
        return foal;
    }

    /** Aceita o chão das ilhas celestes (o Animal vanilla só gosta de grama ou de lugares claros). */
    @Override
    public float getWalkTargetValue(net.minecraft.core.BlockPos pos, net.minecraft.world.level.LevelReader level) {
        return 0.0F;
    }

    /** Sem barra de pulo: o pulo decola (ver {@link MountFlight}). */
    @Override
    public boolean canJump() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    /** Servidor: pilotado = sem gravidade (o cliente move); evita o kick de "veículo flutuando". */
    @Override
    protected double getDefaultGravity() {
        if (!level().isClientSide && getControllingPassenger() instanceof Player) return 0.0;
        return super.getDefaultGravity();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() != null && hasPassenger(source.getEntity())) return false;
        return super.hurt(source, amount);
    }

    // =====================================================================================
    //  FlyingMount
    // =====================================================================================

    @Override
    public Mob asMob() {
        return this;
    }

    @Override
    public MountFlight flight() {
        return flight;
    }

    @Override
    public void setFlyingMode(boolean flying) {
        if (level().isClientSide || flying == flyingMode || groundNavigation == null) return;
        flyingMode = flying;
        navigation.stop();
        if (flying) {
            navigation = airNavigation;
            moveControl = airMoveControl;
        } else {
            navigation = groundNavigation;
            moveControl = groundMoveControl;
            setNoGravity(false);
        }
    }

    @Override
    public boolean isFlyingMode() {
        return flyingMode;
    }

    @Override
    public void requestFlight() {
        flightIntent = 10;
        setFlyingMode(true);
    }

    @Override
    public boolean isWildMount() {
        return !isTamed();
    }

    @Override
    public boolean isMountOwnedBy(Player player) {
        return isTamed() && player.getUUID().equals(getOwnerUUID());
    }

    @Override
    public boolean isMountSaddled() {
        return isSaddled();
    }

    @Override
    public void answerCall(Player player) {
        if (!isMountOwnedBy(player) || isVehicle()) return;
        caller = player.getUUID();
        callTicks = 700;
        setEating(false);
    }

    @Override
    @Nullable
    public UUID caller() {
        return caller;
    }

    @Override
    public void clearCall() {
        caller = null;
        callTicks = 0;
    }

    // =====================================================================================
    //  Habilidade: Galope Celestial
    // =====================================================================================

    @Override
    public String abilityId() {
        return "pegasus_gallop";
    }

    @Override
    public int abilityCooldown() {
        return (int) Math.max(0L, entityData.get(DATA_ABILITY_READY) - level().getGameTime());
    }

    @Override
    public int abilityCooldownMax() {
        return 400;
    }

    @Override
    public int abilityActive() {
        return (int) Math.max(0L, entityData.get(DATA_ABILITY_END) - level().getGameTime());
    }

    @Override
    public double flightSpeedMultiplier() {
        return abilityActive() > 0 ? 1.7 : 1.0;
    }

    @Override
    public boolean tryAbility(ServerPlayer rider) {
        if (!isMountOwnedBy(rider) || rider.getVehicle() != this) return false;
        int cd = abilityCooldown();
        if (cd > 0) {
            rider.displayClientMessage(Component.translatable("message.isekai_mahou.mount_ability_cooldown",
                    abilityName(), String.format("%.1f", cd / 20.0F)), true);
            return false;
        }
        long now = level().getGameTime();
        entityData.set(DATA_ABILITY_READY, now + abilityCooldownMax());
        entityData.set(DATA_ABILITY_END, now + 80);
        rider.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0, false, true));
        addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1, false, true));
        triggerAnim("action", "bless");
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, getSoundSource(), 1.0F, 1.6F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.HORSE_ANGRY, getSoundSource(), 1.0F, 1.3F);
        if (level() instanceof ServerLevel sl) ElementFx.ring(sl, position().add(0, 0.3, 0), Element.LIGHT, 2.0, 28);
        return true;
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) (getAttributeValue(Attributes.MOVEMENT_SPEED) * flightSpeedMultiplier());
    }

    // =====================================================================================
    //  Movimento
    // =====================================================================================

    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance() && isAlive()) {
            if (getControllingPassenger() instanceof Player rider) {
                if (flight.travelRidden(this, rider, input, flightSpeedMultiplier(), false)) return;
                super.travel(input);
                flight.clampFall(this);
                return;
            }
            if (flyingMode && !isInWater() && !isInLava()) {
                flight.travelWild(this);
                return;
            }
        }
        super.travel(input);
        if (isControlledByLocalInstance()) flight.clampFall(this);
    }

    @Override
    public void tick() {
        super.tick();
        flight.tick(this);
        if (level() instanceof ServerLevel sl) {
            int active = abilityActive();
            // v1.3 (desempenho): partículas da habilidade a cada 2 ticks (2 pacotes por tick para todos por perto era demais)
            if (active > 0 && (tickCount & 1) == 0) {
                sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.6, getZ(), 2, 0.4, 0.3, 0.4, 0.01);
                sl.sendParticles(ElementFx.dust(Element.LIGHT, 1.3F), getX(), getY() + 0.2, getZ(), 2, 0.5, 0.1, 0.5, 0.0);
            }
            MountFlight.takeoffFx(this, flight);
            if (flight.flapSoundDue()) playSound(PARAMS.flapSound(), 0.5F, PARAMS.flapPitch() + random.nextFloat() * 0.1F);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (flightIntent > 0) flightIntent--;
        if (isVehicle()) {
            if (flyingMode) setFlyingMode(false);
            if (isNoGravity()) setNoGravity(false);
        } else if (flyingMode && flightIntent == 0 && (onGround() || isInWater())) {
            setFlyingMode(false);
        }
        flight.guardAgainstVoid(this, this, flightIntent > 0);
        if (caller != null && --callTicks <= 0) clearCall();
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HORSE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HORSE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HORSE_DEATH;
    }

    @Override
    protected SoundEvent getEatingSound() {
        return SoundEvents.HORSE_EAT;
    }

    @Override
    protected SoundEvent getAngrySound() {
        return SoundEvents.HORSE_ANGRY;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.12F;
    }

    // =====================================================================================
    //  Save
    // =====================================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("AbilityCooldown", abilityCooldown());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int cd = tag.getInt("AbilityCooldown");
        if (cd > 0) entityData.set(DATA_ABILITY_READY, level().getGameTime() + cd);
        // Salvo no meio do voo: o NoGravity gravado o deixaria flutuando parado para sempre
        setNoGravity(false);
    }

    // =====================================================================================
    //  Animações
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (flight.isAirborne()) return state.setAndContinue(flight.isGliding() ? GLIDE : FLY);
            if (isStanding()) return state.setAndContinue(REAR);
            if (isEating()) return state.setAndContinue(GRAZE);
            if (state.isMoving()) return state.setAndContinue(state.getLimbSwingAmount() > 0.6F ? GALLOP : WALK);
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("bless", BLESS));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
