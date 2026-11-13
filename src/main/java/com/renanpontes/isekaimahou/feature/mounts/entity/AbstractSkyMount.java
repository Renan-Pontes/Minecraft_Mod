package com.renanpontes.isekaimahou.feature.mounts.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base das montarias voadoras domáveis (Grifo, Arraia do Céu, Wyvern).
 *
 * <ul>
 *   <li>Doma: cada subclasse define em {@link #interactWild}; domada fica sentada esperando.</li>
 *   <li>Dono: comida cura e faz cruzar; sela (o item vanilla) habilita montar; tesoura tira a sela;
 *       mão vazia monta; agachado + clique alterna sentar/seguir.</li>
 *   <li>Voo montado: {@link MountFlight} (pulo sobe, agachar desce); tecla do módulo = habilidade.</li>
 *   <li>IA: troca navegação/controle de movimento entre chão e ar ({@link #setFlyingMode}); sem
 *       objetivo pedindo voo ela plana e pousa sozinha.</li>
 * </ul>
 */
public abstract class AbstractSkyMount extends TamableAnimal implements GeoEntity, Saddleable, FlyingMount {
    private static final EntityDataAccessor<Boolean> DATA_SADDLED = SynchedEntityData.defineId(AbstractSkyMount.class, EntityDataSerializers.BOOLEAN);
    /** Tick do jogo em que a habilidade volta a ficar disponível / em que o efeito ativo acaba (sincroniza só 1x). */
    private static final EntityDataAccessor<Long> DATA_ABILITY_READY = SynchedEntityData.defineId(AbstractSkyMount.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_ABILITY_END = SynchedEntityData.defineId(AbstractSkyMount.class, EntityDataSerializers.LONG);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    protected final MountFlight flight;
    private final PathNavigation groundNavigation;
    private final FlyingPathNavigation airNavigation;
    private final MoveControl groundMoveControl;
    private final MountFlyMoveControl airMoveControl;
    private boolean flyingMode;
    private int flightIntent;
    @Nullable
    private UUID caller;
    private int callTicks;

    protected AbstractSkyMount(EntityType<? extends AbstractSkyMount> type, Level level, MountFlight.Params params) {
        super(type, level);
        this.flight = new MountFlight(params);
        this.groundNavigation = this.navigation;
        this.groundMoveControl = this.moveControl;
        this.airNavigation = new FlyingPathNavigation(this, level);
        this.airNavigation.setCanOpenDoors(false);
        this.airNavigation.setCanFloat(true);
        this.airNavigation.setCanPassDoors(true);
        this.airMoveControl = new MountFlyMoveControl(this, 18F, hoversInAir());
        setTame(false, false);
        if (alwaysFlying()) setFlyingMode(true);
    }

    // =====================================================================================
    //  Ganchos das subclasses
    // =====================================================================================

    /** Nunca pousa (Arraia do Céu). */
    public boolean alwaysFlying() {
        return false;
    }

    /** Sem alvo de movimento, fica parada no ar em vez de planar até o chão. */
    protected boolean hoversInAir() {
        return alwaysFlying();
    }

    /** Interação com a criatura SELVAGEM (doma). Devolva PASS para ignorar. */
    protected abstract InteractionResult interactWild(Player player, InteractionHand hand, ItemStack stack);

    /** Comida que o dono usa para curar. */
    protected abstract boolean isHealingFood(ItemStack stack);

    protected float healAmount(ItemStack stack) {
        var food = stack.getFoodProperties(this);
        return food != null ? Math.max(4.0F, food.nutrition() * 2.0F) : 6.0F;
    }

    protected abstract double wildMaxHealth();

    protected abstract double tamedMaxHealth();

    /** Começa a habilidade. {@code rider} = quem montado apertou a tecla; {@code target} = alvo da IA selvagem. */
    protected abstract boolean startAbility(@Nullable LivingEntity rider, @Nullable LivingEntity target);

    /** Duração do efeito ativo (ticks). */
    protected abstract int abilityDuration();

    /** Cada tick do efeito ativo (servidor). */
    protected void abilityTick(int ticksLeft) {}

    protected abstract void playFlapSound();

    // =====================================================================================
    //  Dados
    // =====================================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SADDLED, false);
        builder.define(DATA_ABILITY_READY, 0L);
        builder.define(DATA_ABILITY_END, 0L);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Saddled", isSaddled());
        tag.putInt("AbilityCooldown", abilityCooldown());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSaddled(tag.getBoolean("Saddled"));
        int cd = tag.getInt("AbilityCooldown");
        if (cd > 0) entityData.set(DATA_ABILITY_READY, level().getGameTime() + cd);
        // Salva no meio do voo, o NoGravity ficaria gravado e ela flutuaria parada para sempre ao recarregar
        // (o modo de voo não é salvo: volta pelo chão e os objetivos levantam voo de novo quando precisarem).
        if (!alwaysFlying()) setNoGravity(false);
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
        if (level().isClientSide || flying == flyingMode) return;
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
        return !isTame();
    }

    @Override
    public boolean isMountOwnedBy(Player player) {
        return isTame() && isOwnedBy(player);
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
        setOrderedToSit(false);
        setInSittingPose(false);
        setTarget(null);
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

    @Override
    public int abilityCooldown() {
        return (int) Math.max(0L, entityData.get(DATA_ABILITY_READY) - level().getGameTime());
    }

    @Override
    public int abilityActive() {
        return (int) Math.max(0L, entityData.get(DATA_ABILITY_END) - level().getGameTime());
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
        if (!startAbility(rider, null)) return false;
        armAbility();
        return true;
    }

    /** A IA selvagem também usa a habilidade (com a mesma recarga). */
    protected boolean tryWildAbility(LivingEntity target) {
        if (abilityCooldown() > 0 || abilityActive() > 0) return false;
        if (!startAbility(null, target)) return false;
        armAbility();
        return true;
    }

    private void armAbility() {
        long now = level().getGameTime();
        entityData.set(DATA_ABILITY_READY, now + abilityCooldownMax());
        entityData.set(DATA_ABILITY_END, now + abilityDuration());
    }

    // =====================================================================================
    //  Doma, dono, interação
    // =====================================================================================

    @Override
    protected void applyTamingSideEffects() {
        AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp == null) return;
        if (isTame()) {
            hp.setBaseValue(tamedMaxHealth());
            setHealth(getMaxHealth());
        } else {
            hp.setBaseValue(wildMaxHealth());
        }
    }

    /** Tentativa de doma com 1 chance em {@code oneIn}; dispara o evento do NeoForge. */
    protected boolean tryTame(Player player, int oneIn) {
        if (random.nextInt(Math.max(1, oneIn)) == 0 && !EventHooks.onAnimalTame(this, player)) {
            tame(player);
            navigation.stop();
            setTarget(null);
            setOrderedToSit(true);
            setFlyingMode(alwaysFlying());
            level().broadcastEntityEvent(this, (byte) 7);
            MountFx.tamed(this, player, ParticleTypes.HAPPY_VILLAGER);
            return true;
        }
        level().broadcastEntityEvent(this, (byte) 6);
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean client = level().isClientSide;
        if (isVehicle()) return InteractionResult.PASS;

        if (!isTame()) {
            InteractionResult wild = interactWild(player, hand, stack);
            // v1.0: mão vazia numa selvagem diz o que ela quer (antes o clique não fazia nada e ninguém descobria a doma)
            if (wild == InteractionResult.PASS && stack.isEmpty() && hand == InteractionHand.MAIN_HAND && !isBaby()) {
                if (!client) player.displayClientMessage(tameHint(), true);
                return InteractionResult.sidedSuccess(client);
            }
            return wild;
        }

        if (!isOwnedBy(player)) {
            if (!client && (stack.isEmpty() || stack.is(Items.SADDLE))) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.mount_not_owner"), true);
            }
            // Consome a interação com a sela: senão o SaddleItem vanilla selaria a montaria de outra pessoa
            return stack.is(Items.SADDLE) ? InteractionResult.sidedSuccess(client) : InteractionResult.PASS;
        }

        if (isHealingFood(stack) && getHealth() < getMaxHealth()) {
            if (!client) {
                heal(healAmount(stack));
                stack.consume(1, player);
                level().broadcastEntityEvent(this, (byte) 7);
                playSound(SoundEvents.GENERIC_EAT, 0.8F, 0.9F);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (stack.is(Items.SADDLE)) {
            if (isSaddleable() && !isSaddled()) {
                if (!client) {
                    equipSaddle(stack.copyWithCount(1), SoundSource.NEUTRAL);
                    stack.consume(1, player);
                    player.displayClientMessage(Component.translatable("message.isekai_mahou.mount_saddled", getDisplayName()), true);
                }
                return InteractionResult.sidedSuccess(client);
            }
            return InteractionResult.PASS;
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
        if (!player.isSecondaryUseActive() && isSaddled() && !isBaby()) {
            if (!client) doRide(player);
            return InteractionResult.sidedSuccess(client);
        }
        if (!client) {
            boolean sit = !isOrderedToSit();
            setOrderedToSit(sit);
            jumping = false;
            navigation.stop();
            setTarget(null);
            clearCall();
            player.displayClientMessage(Component.translatable(sit ? "message.isekai_mahou.mount_sit" : "message.isekai_mahou.mount_follow",
                    getDisplayName()), true);
        }
        return InteractionResult.sidedSuccess(client);
    }

    protected void doRide(Player player) {
        setOrderedToSit(false);
        setInSittingPose(false);
        navigation.stop();
        setTarget(null);
        clearCall();
        setFlyingMode(alwaysFlying());
        player.setYRot(getYRot());
        player.setXRot(getXRot());
        if (player.startRiding(this) && player instanceof ServerPlayer sp) MountFx.controlsHint(sp, this);
    }

    @Override
    public boolean canMate(Animal other) {
        return other != this && other.getClass() == getClass() && other instanceof AbstractSkyMount m && isTame() && m.isTame()
                && !isInSittingPose() && !m.isInSittingPose() && isInLove() && m.isInLove();
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        Entity baby = getType().create(level);
        if (!(baby instanceof AbstractSkyMount child)) return null;
        UUID owner = getOwnerUUID();
        if (owner != null) {
            child.setOwnerUUID(owner);
            child.setTame(true, true);
        }
        return child;
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
        if (source != null) level().playSound(null, this, getSaddleSoundEvent(), source, 0.6F, 0.9F);
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
        float yaw = net.minecraft.util.Mth.approachDegrees(getYRot(), player.getYRot(), flight.params().turnRate());
        setRot(yaw, player.getXRot() * 0.5F);
        yBodyRot = yHeadRot = getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travel) {
        float strafe = player.xxa * 0.5F;
        float forward = player.zza;
        if (forward <= 0.0F) forward *= 0.35F;
        return new Vec3(strafe, 0.0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) (getAttributeValue(Attributes.MOVEMENT_SPEED) * flightSpeedMultiplier());
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (passenger instanceof LivingEntity le) le.yBodyRot = yBodyRot;
    }

    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance() && isAlive()) {
            if (getControllingPassenger() instanceof Player rider) {
                if (flight.travelRidden(this, rider, input, flightSpeedMultiplier(), isDashing())) return;
                super.travel(input);
                flight.clampFall(this);
                return;
            }
            if (flyingMode && (alwaysFlying() || !isInWater() && !isInLava())) {
                flight.travelWild(this);
                return;
            }
        }
        super.travel(input);
        if (isControlledByLocalInstance()) flight.clampFall(this);
    }

    /**
     * O Animal vanilla só aceita grama ou lugares claros (valor negativo no escuro), o que impediria o
     * spawn natural no Abismo, em picos de pedra e na geração do mundo. Montarias andam em qualquer chão.
     */
    @Override
    public float getWalkTargetValue(net.minecraft.core.BlockPos pos, net.minecraft.world.level.LevelReader level) {
        return 0.0F;
    }

    /**
     * No servidor, a montaria pilotada não é simulada (o cliente move). Gravidade 0 ali faz o servidor
     * não expulsar o jogador por "veículo flutuando" (allow-flight=false), que mediria pela gravidade.
     */
    @Override
    protected double getDefaultGravity() {
        if (!level().isClientSide && getControllingPassenger() instanceof Player) return 0.0;
        return super.getDefaultGravity();
    }

    /** Voadoras não se machucam com queda (e protegem quem está montado). */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        Entity attacker = source.getEntity();
        // O próprio cavaleiro (golpes errados, magias de área) não fere a montaria
        if (attacker != null && hasPassenger(attacker)) return false;
        if (!level().isClientSide) setOrderedToSit(false);
        return super.hurt(source, amount);
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================

    @Override
    public void tick() {
        super.tick();
        flight.tick(this);
        if (!level().isClientSide) {
            int active = abilityActive();
            if (active > 0) abilityTick(active);
            MountFlight.takeoffFx(this, flight);
            if (flight.flapSoundDue()) playFlapSound();
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (flightIntent > 0) flightIntent--;
        if (alwaysFlying()) {
            if (!flyingMode) setFlyingMode(true);
            if (!isNoGravity()) setNoGravity(true);
        } else if (isVehicle()) {
            if (flyingMode) setFlyingMode(false);
            if (isNoGravity()) setNoGravity(false);
        } else if (flyingMode && flightIntent == 0 && (onGround() || isInWater())) {
            setFlyingMode(false);
        }
        if (!alwaysFlying()) flight.guardAgainstVoid(this, this, flightIntent > 0);
        if (caller != null && --callTicks <= 0) clearCall();
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
