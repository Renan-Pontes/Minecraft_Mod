package com.renanpontes.isekaimahou.feature.mounts.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base das montarias de CHÃO (v1.0): Dragão Terrestre, Pesadelo e Cervo da Aurora.
 *
 * <ul>
 *   <li>Chegam prontas pelas <b>Rédeas</b> (já domadas e seladas) — é o jeito simples de ter montaria. As espécies
 *       que existem soltas no mundo também podem ser domadas ({@link #interactWild}) e seladas com a sela vanilla.</li>
 *   <li>Dono: mão vazia monta; agachado + clique alterna esperar/seguir; comida cura; o Apito chama.</li>
 *   <li>Montado: anda para onde o jogador olha (como cavalo), sobe degraus de 1 bloco sozinha; a tecla do módulo usa
 *       a habilidade.</li>
 *   <li>Se a montaria domada morre, as Rédeas dela voltam para o dono (não se perde a montaria para sempre).</li>
 * </ul>
 */
public abstract class GroundMount extends TamableAnimal implements GeoEntity, Saddleable, RideMount {
    private static final EntityDataAccessor<Boolean> DATA_SADDLED = SynchedEntityData.defineId(GroundMount.class, EntityDataSerializers.BOOLEAN);
    /** Tick do jogo em que a habilidade volta / em que o efeito ativo acaba (sincroniza só 1x). */
    private static final EntityDataAccessor<Long> DATA_ABILITY_READY = SynchedEntityData.defineId(GroundMount.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Long> DATA_ABILITY_END = SynchedEntityData.defineId(GroundMount.class, EntityDataSerializers.LONG);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String typeId;
    private final RawAnimation animIdle;
    private final RawAnimation animWalk;
    private final RawAnimation animRun;
    private final RawAnimation animSit;
    @Nullable
    private UUID caller;
    private int callTicks;

    protected GroundMount(EntityType<? extends GroundMount> type, Level level) {
        super(type, level);
        this.typeId = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        this.animIdle = RawAnimation.begin().thenLoop("animation." + typeId + ".idle");
        this.animWalk = RawAnimation.begin().thenLoop("animation." + typeId + ".walk");
        this.animRun = RawAnimation.begin().thenLoop("animation." + typeId + ".run");
        this.animSit = RawAnimation.begin().thenLoop("animation." + typeId + ".sit");
        setTame(false, false);
    }

    // =====================================================================================
    //  Ganchos das subclasses
    // =====================================================================================

    /** Interação com a criatura SELVAGEM (doma). PASS = esta espécie não se doma no mundo. */
    protected InteractionResult interactWild(Player player, InteractionHand hand, ItemStack stack) {
        return InteractionResult.PASS;
    }

    /** Comida que o dono usa para curar (e que atrai a criatura). */
    protected abstract boolean isHealingFood(ItemStack stack);

    protected abstract double wildMaxHealth();

    protected abstract double tamedMaxHealth();

    /** Começa a habilidade (servidor). */
    protected abstract boolean startAbility(ServerPlayer rider);

    /** Duração do efeito ativo (ticks). */
    protected abstract int abilityDuration();

    /** Cada tick do efeito ativo (servidor). */
    protected void abilityTick(int ticksLeft) {}

    /** Multiplicador de velocidade agora (habilidades de corrida). */
    protected double speedMultiplier() {
        return 1.0;
    }

    /** Roda no lado que controla o movimento (o cliente de quem pilota): saltos e impulsos da habilidade. */
    protected void riddenControlTick(Player rider) {}

    // =====================================================================================
    //  IA
    // =====================================================================================

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        // Só a selvagem foge ao apanhar: a domada fica ao lado do dono
        goalSelector.addGoal(2, new PanicGoal(this, 1.5) {
            @Override
            public boolean canUse() {
                return isWildMount() && super.canUse();
            }
        });
        goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.25, 9F, 3F));
        goalSelector.addGoal(4, new TemptGoal(this, 1.1, this::isHealingFood, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (caller == null) return;
        Player p = level().getPlayerByUUID(caller);
        if (p == null || --callTicks <= 0 || isVehicle() || distanceToSqr(p) < 9.0) {
            clearCall();
            return;
        }
        // Longe demais (ou sem caminho): aparece ao lado do dono, como os lobos fazem
        if (distanceToSqr(p) > 24.0 * 24.0 || callTicks % 60 == 0 && navigation.isDone()) {
            tryToTeleportToOwner();
        } else if (callTicks % 10 == 0) {
            navigation.moveTo(p, 1.5);
        }
    }

    private void clearCall() {
        caller = null;
        callTicks = 0;
    }

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
    }

    // =====================================================================================
    //  RideMount
    // =====================================================================================

    @Override
    public Mob asMob() {
        return this;
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
        callTicks = 600;
        setOrderedToSit(false);
        setInSittingPose(false);
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
        if (!startAbility(rider)) return false;
        long now = level().getGameTime();
        entityData.set(DATA_ABILITY_READY, now + abilityCooldownMax());
        entityData.set(DATA_ABILITY_END, now + abilityDuration());
        return true;
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

        if (!isTame()) return interactWild(player, hand, stack);

        if (!isOwnedBy(player)) {
            if (!client && (stack.isEmpty() || stack.is(Items.SADDLE))) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.mount_not_owner"), true);
            }
            return stack.is(Items.SADDLE) ? InteractionResult.sidedSuccess(client) : InteractionResult.PASS;
        }
        if (isHealingFood(stack) && getHealth() < getMaxHealth()) {
            if (!client) {
                heal(6.0F);
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
        if (!player.isSecondaryUseActive() && isSaddled()) {
            if (!client) doRide(player);
            return InteractionResult.sidedSuccess(client);
        }
        if (!client) {
            boolean sit = !isOrderedToSit();
            setOrderedToSit(sit);
            jumping = false;
            navigation.stop();
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
        clearCall();
        player.setYRot(getYRot());
        player.setXRot(getXRot());
        if (player.startRiding(this) && player instanceof ServerPlayer sp) MountFx.controlsHint(sp, this);
    }

    /** Montarias de chão não cruzam (chegam pelas Rédeas ou pela doma). */
    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    /** Anda em qualquer chão (o Animal vanilla só gosta de grama e lugares claros). */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    // =====================================================================================
    //  Rédeas: a montaria domada nunca se perde de vez
    // =====================================================================================

    /** Item das Rédeas desta espécie ({@code <id>_reins}); null se não existir. */
    @Nullable
    public Item reinsItem() {
        Item item = BuiltInRegistries.ITEM.get(IsekaiMahou.id(typeId + "_reins"));
        return item == Items.AIR ? null : item;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && isTame() && !isRemoved()) {
            Item reins = reinsItem();
            if (reins != null) {
                ItemStack stack = new ItemStack(reins);
                LivingEntity owner = getOwner();
                if (owner instanceof ServerPlayer sp && sp.isAlive() && sp.distanceToSqr(this) < 64.0 * 64.0) {
                    if (!sp.getInventory().add(stack)) sp.drop(stack, false);
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.mount_reins_back", getDisplayName()), false);
                } else {
                    ItemEntity drop = new ItemEntity(level(), getX(), getY() + 0.5, getZ(), stack);
                    // Só some se alguém pegar: o dono pode voltar para buscar
                    drop.setUnlimitedLifetime();
                    drop.setInvulnerable(true);
                    if (getOwnerUUID() != null) drop.setTarget(getOwnerUUID());
                    level().addFreshEntity(drop);
                }
            }
        }
        super.die(source);
    }

    /** A sela vai junto com as Rédeas (senão daria para duplicar selas matando a montaria). */
    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (isSaddled() && (!isTame() || reinsItem() == null)) spawnAtLocation(Items.SADDLE);
    }

    // =====================================================================================
    //  Sela e montaria
    // =====================================================================================

    @Override
    public boolean isSaddleable() {
        return isAlive() && isTame();
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
        if (isControlledByLocalInstance()) riddenControlTick(player);
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travel) {
        float strafe = player.xxa * 0.5F;
        float forward = player.zza;
        if (forward <= 0.0F) forward *= 0.25F;
        return new Vec3(strafe, 0.0, forward);
    }

    @Override
    protected float getRiddenSpeed(Player player) {
        return (float) (getAttributeValue(Attributes.MOVEMENT_SPEED) * speedMultiplier());
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        super.positionRider(passenger, callback);
        if (passenger instanceof LivingEntity le) le.yBodyRot = yBodyRot;
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

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            int active = abilityActive();
            if (active > 0) abilityTick(active);
        }
    }

    // =====================================================================================
    //  Animações
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (isInSittingPose()) return state.setAndContinue(animSit);
            if (state.isMoving()) {
                return state.setAndContinue(abilityActive() > 0 || state.getLimbSwingAmount() > 0.6F ? animRun : animWalk);
            }
            return state.setAndContinue(animIdle);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
