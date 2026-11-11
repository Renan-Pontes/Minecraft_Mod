package com.renanpontes.isekaimahou.feature.class_ranger.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerCompanions;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerProgress;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Corcel do Ranger (Trilha do Ranger rank II): cavalo-montaria forte e já selado, chamado pelo glifo Chamado do
 * Corcel. Só o dono monta (sem inventário); a velocidade, o pulo, a vida e a armadura crescem com a classe. Sem
 * cavaleiro por um minuto, volta para a mata (some) — chame de novo quando precisar.
 */
public class RangerSteedEntity extends AbstractHorse implements GeoEntity {
    public static final String ID = "ranger_steed";
    private static final ResourceLocation HEALTH_ID = IsekaiMahou.id("ranger_steed_health");
    private static final ResourceLocation SPEED_ID = IsekaiMahou.id("ranger_steed_speed");
    private static final ResourceLocation JUMP_ID = IsekaiMahou.id("ranger_steed_jump");
    private static final ResourceLocation ARMOR_ID = IsekaiMahou.id("ranger_steed_armor");
    private static final int IDLE_VANISH = 60 * 20;
    private static final int WILD_LIFETIME = 5 * 60 * 20;
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(RangerSteedEntity.class, EntityDataSerializers.INT);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.ranger_steed.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.ranger_steed.walk");
    private static final RawAnimation GALLOP = RawAnimation.begin().thenLoop("animation.ranger_steed.gallop");
    private static final RawAnimation REAR = RawAnimation.begin().thenPlay("animation.ranger_steed.rear");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int idleTicks;
    private double rideAccum;
    @Nullable
    private net.minecraft.world.phys.Vec3 lastPos;

    public RangerSteedEntity(EntityType<? extends RangerSteedEntity> type, Level level) {
        super(type, level);
        setTamed(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createBaseHorseAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.29)
                .add(Attributes.JUMP_STRENGTH, 0.72)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEVEL, 1);
    }

    public int companionLevel() {
        return entityData.get(DATA_LEVEL);
    }

    public int stage() {
        return RangerCompanions.stageOf(companionLevel());
    }

    /** Vida, velocidade, pulo e armadura pelo nível (1..10). */
    public void applyLevel(int lvl) {
        lvl = Math.max(1, Math.min(RangerCompanion.MAX_LEVEL, lvl));
        int old = companionLevel();
        entityData.set(DATA_LEVEL, lvl);
        float before = getMaxHealth();
        set(Attributes.MAX_HEALTH, HEALTH_ID, 8.0 * (lvl - 1));
        set(Attributes.MOVEMENT_SPEED, SPEED_ID, 0.0085 * (lvl - 1));
        set(Attributes.JUMP_STRENGTH, JUMP_ID, 0.03 * (lvl - 1));
        set(Attributes.ARMOR, ARMOR_ID, 0.8 * (lvl - 1));
        if (lvl > old || getHealth() > getMaxHealth()) setHealth(Math.min(getMaxHealth(), getHealth() + Math.max(0, getMaxHealth() - before)));
    }

    private void set(Holder<Attribute> attr, ResourceLocation id, double v) {
        AttributeInstance inst = getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (v != 0) inst.addPermanentModifier(new AttributeModifier(id, v, AttributeModifier.Operation.ADD_VALUE));
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    /** Já nasce selado: a sela é do espírito. */
    @Override
    public boolean isSaddled() {
        return true;
    }

    @Override
    public boolean isSaddleable() {
        return false;
    }

    /** Só o dono monta; sem inventário (o corcel é um espírito). Corcel de ovo (sem dono): qualquer um. */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isVehicle() || isBaby()) return InteractionResult.PASS;
        if (getOwnerUUID() != null && !getOwnerUUID().equals(player.getUUID())) {
            if (!level().isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.isekai_mahou.class_ranger.steed_not_yours"), true);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        doPlayerRide(player);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        // sem inventário
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canMate(Animal other) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob other) {
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (getOwnerUUID() != null && source.getEntity() != null && getOwnerUUID().equals(source.getEntity().getUUID())) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        LivingEntity rider = getControllingPassenger();
        // o cliente do cavaleiro move o corcel entre os ticks: mede pela posição do tick anterior
        double moved = lastPos == null ? 0 : Math.sqrt(Math.pow(getX() - lastPos.x, 2) + Math.pow(getZ() - lastPos.z, 2));
        lastPos = position();
        if (rider != null) {
            idleTicks = 0;
            if (rider instanceof ServerPlayer sp && getOwnerUUID() != null && getOwnerUUID().equals(sp.getUUID())) {
                // distância cavalgada conta para a Trilha do Ranger (a cada 16 blocos)
                if (moved < 3.0) rideAccum += moved;
                if (rideAccum >= 16) {
                    int n = (int) rideAccum;
                    rideAccum -= n;
                    RangerProgress.count(sp, RangerProgress.RIDE, n);
                }
                if (tickCount % 40 == 0 && getHealth() < getMaxHealth()) heal(1.0F + companionLevel() * 0.2F);
            }
            if (stage() == 2 && tickCount % 3 == 0 && moved > 0.2) {
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 0.3, getZ(), 1, 0.3, 0.1, 0.3, 0);
            }
            return;
        }
        idleTicks++;
        if (getOwnerUUID() == null ? tickCount > WILD_LIFETIME : (idleTicks > IDLE_VANISH || !RangerCompanions.isCurrentSteed(sl, this))) {
            vanish(sl);
        }
    }

    /** Volta para a mata em uma nuvem de folhas. */
    public void vanish(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.8, getZ(), 16, 0.5, 0.5, 0.5, 0.03);
        sl.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + 1.0, getZ(), 12, 0.6, 0.5, 0.6, 0.02);
        playSound(SoundEvents.HORSE_BREATHE, 1.0F, 1.2F);
        discard();
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel sl) RangerCompanions.onSteedDeath(sl, this);
    }

    /** Relincho ao ser chamado. */
    public void neigh() {
        standIfPossible();
        triggerAnim("action", "rear");
        playSound(SoundEvents.HORSE_AMBIENT, 1.2F, 0.9F);
    }

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
    protected SoundEvent getAngrySound() {
        return SoundEvents.HORSE_ANGRY;
    }

    @Override
    protected int getBaseExperienceReward() {
        return 0;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("CompanionLevel", companionLevel());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setTamed(true);
        if (tag.contains("CompanionLevel")) applyLevel(tag.getInt("CompanionLevel"));
    }

    /** Nível pelo dono (usado ao chamar). */
    public void refreshFromOwner(ServerPlayer sp) {
        applyLevel(RangerCompanions.level(MagicApi.get(sp)));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<RangerSteedEntity>(this, "movement", 4, state -> {
            double v = getDeltaMovement().horizontalDistanceSqr();
            if (!state.isMoving() && v < 1.0E-4) return state.setAndContinue(IDLE);
            return state.setAndContinue(v > 0.04 ? GALLOP : WALK);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP).triggerableAnim("rear", REAR));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
