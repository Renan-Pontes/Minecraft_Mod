package com.renanpontes.isekaimahou.feature.creatures_c.entity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Mímico: um baú perfeito, parado, alinhado aos blocos (dá até para subir em cima). Quando alguém chega perto
 * demais, clica nele ou o acerta, a tampa se abre numa bocarra cheia de dentes e ele morde de surpresa.
 * Sem alvo por um tempo, volta a se disfarçar (e se cura devagar). Deixa cair loot bom.
 */
public class Mimic extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_DISGUISED = SynchedEntityData.defineId(Mimic.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation DISGUISED = RawAnimation.begin().thenLoop("animation.mimic.disguised");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mimic.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.mimic.walk");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("animation.mimic.bite");
    private static final RawAnimation WAKE = RawAnimation.begin().thenPlay("animation.mimic.wake");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int idleTicks;
    private int surpriseTicks;
    @Nullable
    private LivingEntity surpriseTarget;

    public Mimic(EntityType<? extends Mimic> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 35.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DISGUISED, true);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.35, true));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public boolean isDisguised() {
        return entityData.get(DATA_DISGUISED);
    }

    private void setDisguised(boolean disguised) {
        entityData.set(DATA_DISGUISED, disguised);
    }

    /** Alinha o "baú" a uma das 4 direções, como um bloco de verdade. */
    private void snapRotation() {
        float yaw = Math.round(getYRot() / 90.0F) * 90.0F;
        setYRot(yaw);
        yRotO = yaw;
        setYHeadRot(yaw);
        yHeadRotO = yaw;
        setYBodyRot(yaw);
        yBodyRotO = yaw;
        setXRot(0.0F);
    }

    // =====================================================================================
    //  Disfarce
    // =====================================================================================

    @Override
    protected boolean isImmobile() {
        return isDisguised() || super.isImmobile();
    }

    @Override
    public boolean canBeCollidedWith() {
        return isDisguised() && isAlive();
    }

    @Override
    public boolean isPushable() {
        return !isDisguised() && super.isPushable();
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (!isDisguised()) super.knockback(strength, x, z);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // Alinha nos DOIS lados: no cliente o corpo não acompanha o yRot sozinho e o "baú" ficaria torto
        if (isDisguised() && isAlive()) snapRotation();
        if (!(level() instanceof ServerLevel sl) || !isAlive()) return;
        if (isDisguised()) {
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
            if (tickCount % 40 == 0 && getHealth() < getMaxHealth()) heal(1.0F);
            if (tickCount % 4 == 0) {
                Player p = sl.getNearestPlayer(getX(), getY(), getZ(), 4.0, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
                if (p != null && distanceTo(p) < triggerDistance(p)) wake(p, true);
            }
            return;
        }
        if (surpriseTicks > 0 && --surpriseTicks == 0 && surpriseTarget != null) {
            if (surpriseTarget.isAlive() && distanceToSqr(surpriseTarget) < 6.25) doHurtTarget(surpriseTarget);
            surpriseTarget = null;
        }
        if (getTarget() == null) {
            if (++idleTicks > 140 && onGround()) disguise();
        } else {
            idleTicks = 0;
            // Batidas de tampa de vez em quando: o "rosnado" do mímico
            if (random.nextInt(45) == 0) playSound(random.nextBoolean() ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE, 0.6F, 0.6F + random.nextFloat() * 0.3F);
        }
    }

    /** Quanto mais rápido o jogador vem, de mais longe ele percebe; agachado dá para chegar mais perto. */
    private static double triggerDistance(Player p) {
        if (p.isShiftKeyDown()) return 1.5;
        return p.isSprinting() ? 3.5 : 2.4;
    }

    /** Revela o mímico; com surpresa, morde logo em seguida. */
    public void wake(@Nullable LivingEntity cause, boolean surprise) {
        if (!isDisguised()) return;
        setDisguised(false);
        idleTicks = 0;
        triggerAnim("action", "wake");
        playSound(SoundEvents.CHEST_OPEN, 1.0F, 0.55F);
        playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.0F, 0.8F);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.defaultBlockState()),
                    getX(), getY() + 0.6, getZ(), 18, 0.35, 0.25, 0.35, 0.1);
        }
        if (cause != null && !(cause instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            setTarget(cause);
            if (surprise) {
                surpriseTarget = cause;
                surpriseTicks = 6;
            }
        }
    }

    private void disguise() {
        setDisguised(true);
        idleTicks = 0;
        getNavigation().stop();
        setTarget(null);
        snapRotation();
        // Encaixa no centro do bloco, como um baú colocado de verdade (se couber ali)
        double cx = Mth.floor(getX()) + 0.5, cz = Mth.floor(getZ()) + 0.5;
        if (level().noCollision(this, getBoundingBox().move(cx - getX(), 0.0, cz - getZ()))) setPos(cx, getY(), cz);
        playSound(SoundEvents.CHEST_CLOSE, 1.0F, 0.75F);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (isDisguised()) {
            // Tentou abrir o "baú"...
            if (!level().isClientSide) wake(player, true);
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && isDisguised()) {
            wake(source.getEntity() instanceof LivingEntity le ? le : null, false);
        }
        return hurt;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            triggerAnim("action", "bite");
            playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.0F, 0.9F);
        }
        return hit;
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOOD_STEP, 0.4F, 0.8F);
    }

    // =====================================================================================
    //  Save / animações
    // =====================================================================================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Disguised", isDisguised());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Disguised")) setDisguised(tag.getBoolean("Disguised"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> {
            if (isDisguised()) return state.setAndContinue(DISGUISED);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("bite", BITE)
                .triggerableAnim("wake", WAKE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
