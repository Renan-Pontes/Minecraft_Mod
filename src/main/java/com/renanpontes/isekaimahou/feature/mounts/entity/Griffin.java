package com.renanpontes.isekaimahou.feature.mounts.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.mounts.entity.goal.AerialAttackGoal;
import com.renanpontes.isekaimahou.feature.mounts.entity.goal.AnswerCallGoal;
import com.renanpontes.isekaimahou.feature.mounts.entity.goal.FlyingFollowOwnerGoal;
import com.renanpontes.isekaimahou.feature.mounts.entity.goal.SoarGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Grifo: águia na frente, leão atrás. Vive nas montanhas do Overworld; neutro (revida), caça coelhos,
 * galinhas e cordeiros. Domado com peixe ou carne crua (1 em 5). Montado é a montaria mais rápida no ar.
 * Habilidade "Investida": mergulho na direção do olhar que derruba e fere quem estiver no caminho.
 */
public class Griffin extends AbstractSkyMount {
    public static final String ID = "griffin";
    private static final MountFlight.Params PARAMS = new MountFlight.Params(0.95, 0.42, 0.55, 0.035, 0.09, 0.12, 0.6,
            0.75, false, 0.32, 16F, MountFlight.wingSound(), 1.35F, 14);

    private static final Predicate<LivingEntity> PREY = e -> {
        EntityType<?> t = e.getType();
        return t == EntityType.RABBIT || t == EntityType.CHICKEN || t == EntityType.SHEEP && e.isBaby();
    };

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.griffin.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.griffin.walk");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.griffin.run");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.griffin.fly");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.griffin.glide");
    private static final RawAnimation DIVE = RawAnimation.begin().thenLoop("animation.griffin.dive");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.griffin.sit");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.griffin.attack");
    private static final RawAnimation SCREECH = RawAnimation.begin().thenPlay("animation.griffin.screech");

    /** Quem já foi atingido nesta investida (cada um só uma vez). */
    private final Set<UUID> dashHits = new HashSet<>();

    public Griffin(EntityType<? extends Griffin> type, Level level) {
        super(type, level, PARAMS);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.55)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ARMOR, 3.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AnswerCallGoal<>(this, 1.6));
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(3, new AerialAttackGoal<>(this, 1.2, 22));
        goalSelector.addGoal(4, new FlyingFollowOwnerGoal(this, 1.15, 10F, 3.5F));
        goalSelector.addGoal(5, new BreedGoal(this, 1.0));
        // Peixe/carne crua na mão atrai o grifo (é assim que se chega perto para domar)
        goalSelector.addGoal(5, new TemptGoal(this, 1.1, Griffin::isTameFood, false));
        goalSelector.addGoal(6, new SoarGoal<>(this, 700));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(4, new NonTameRandomTargetGoal<>(this, Animal.class, false, PREY));
    }

    // =====================================================================================
    //  Doma
    // =====================================================================================

    /** Peixe cru ou carne crua. */
    public static boolean isTameFood(ItemStack s) {
        return s.is(Items.COD) || s.is(Items.SALMON) || s.is(Items.TROPICAL_FISH) || s.is(Items.BEEF) || s.is(Items.PORKCHOP)
                || s.is(Items.MUTTON) || s.is(Items.CHICKEN) || s.is(Items.RABBIT);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return isTameFood(stack);
    }

    @Override
    protected boolean isHealingFood(ItemStack stack) {
        return isTameFood(stack) || stack.is(Items.COOKED_BEEF) || stack.is(Items.COOKED_COD) || stack.is(Items.COOKED_SALMON);
    }

    @Override
    protected InteractionResult interactWild(Player player, InteractionHand hand, ItemStack stack) {
        if (!isTameFood(stack) || isBaby()) return InteractionResult.PASS;
        boolean client = level().isClientSide;
        if (getTarget() == player || getLastHurtByMob() == player && tickCount - getLastHurtByMobTimestamp() < 400) {
            if (!client) player.displayClientMessage(Component.translatable("message.isekai_mahou.griffin_angry"), true);
            return InteractionResult.sidedSuccess(client);
        }
        if (!client) {
            stack.consume(1, player);
            playSound(SoundEvents.GENERIC_EAT, 0.9F, 0.8F);
            if (!tryTame(player, 5)) playSound(SoundEvents.PARROT_AMBIENT, 1.0F, 0.6F);
        }
        return InteractionResult.sidedSuccess(client);
    }

    @Override
    protected double wildMaxHealth() {
        return 40.0;
    }

    @Override
    protected double tamedMaxHealth() {
        return 55.0;
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof net.minecraft.world.entity.monster.Creeper || target instanceof net.minecraft.world.entity.monster.Ghast) return false;
        if (target instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame() && t.getOwner() == owner) return false;
        return !(target instanceof Player p && owner instanceof Player o && !o.canHarmPlayer(p));
    }

    // =====================================================================================
    //  Habilidade: Investida
    // =====================================================================================

    @Override
    public String abilityId() {
        return "griffin_dive";
    }

    @Override
    public int abilityCooldownMax() {
        return 120;
    }

    @Override
    protected int abilityDuration() {
        return 14;
    }

    @Override
    public double flightSpeedMultiplier() {
        return abilityActive() > 0 ? 2.0 : 1.0;
    }

    @Override
    public boolean isDashing() {
        return abilityActive() > 0;
    }

    @Override
    protected boolean startAbility(@Nullable LivingEntity rider, @Nullable LivingEntity target) {
        if (rider == null) return false;
        dashHits.clear();
        triggerAnim("action", "screech");
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_SWOOP, getSoundSource(), 1.4F, 0.8F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.PARROT_IMITATE_PHANTOM, getSoundSource(), 1.2F, 0.6F);
        return true;
    }

    @Override
    protected void abilityTick(int ticksLeft) {
        if (!(level() instanceof ServerLevel sl)) return;
        LivingEntity rider = getControllingPassenger();
        Vec3 mv = flight.horizontalSpeed() > 0.01 ? getDeltaMovement() : Vec3.ZERO;
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.8, getZ(), 3, 0.5, 0.3, 0.5, 0.02);
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + mv.x, getY() + 1.0, getZ() + mv.z, 1, 0.3, 0.2, 0.3, 0.0);
        AABB box = getBoundingBox().inflate(1.2, 0.6, 1.2);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box)) {
            if (MountFx.isFriendly(this, e, rider) || !dashHits.add(e.getUUID())) continue;
            var src = rider instanceof Player p ? damageSources().playerAttack(p) : damageSources().mobAttack(this);
            if (e.hurt(src, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) + 3.0F)) {
                Vec3 push = e.position().subtract(position()).multiply(1, 0, 1);
                if (push.lengthSqr() < 1.0E-4) push = getLookAngle();
                push = push.normalize().scale(1.1);
                e.push(push.x, 0.45, push.z);
                e.hurtMarked = true;
                sl.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
                playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.8F);
            }
        }
    }

    // =====================================================================================
    //  Combate e sons
    // =====================================================================================

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            triggerAnim("action", "attack");
            playSound(SoundEvents.PARROT_HURT, 1.0F, 0.5F);
        }
        return hit;
    }

    @Override
    protected void playFlapSound() {
        playSound(flight.params().flapSound(), 0.55F, flight.params().flapPitch() + random.nextFloat() * 0.1F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return random.nextInt(3) == 0 ? SoundEvents.PARROT_IMITATE_PHANTOM : SoundEvents.PARROT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PARROT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PARROT_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOLF_STEP, 0.35F, 0.6F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.55F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.1F;
    }

    // =====================================================================================
    //  Animações
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (isInSittingPose()) return state.setAndContinue(SIT);
            if (flight.isAirborne()) {
                if (abilityActive() > 0) return state.setAndContinue(DIVE);
                return state.setAndContinue(flight.isGliding() ? GLIDE : FLY);
            }
            if (state.isMoving()) return state.setAndContinue(state.getLimbSwingAmount() > 0.55F ? RUN : WALK);
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("screech", SCREECH));
    }
}
