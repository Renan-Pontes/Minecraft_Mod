package com.renanpontes.isekaimahou.feature.mounts.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.mounts.entity.goal.AerialAttackGoal;
import com.renanpontes.isekaimahou.feature.mounts.entity.goal.AnswerCallGoal;
import com.renanpontes.isekaimahou.feature.mounts.entity.goal.FlyingFollowOwnerGoal;
import com.renanpontes.isekaimahou.feature.mounts.entity.goal.SoarGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
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
 * Wyvern do Abismo: dragão de duas patas e asas membranosas. Selvagem é AGRESSIVA (dá rasantes e cospe
 * sopro sombrio). Para domar: jogue-lhe carne podre (ela come e se acalma por 30 s) e, calma, ofereça uma
 * Essência Sombria (1 em 3). Domada é uma montaria pesada e resistente; a tecla do módulo dispara o
 * "Sopro Sombrio" na direção do olhar (dano + Corrompido).
 */
public class Wyvern extends AbstractSkyMount {
    public static final String ID = "wyvern";
    private static final MountFlight.Params PARAMS = new MountFlight.Params(0.78, 0.34, 0.5, 0.04, 0.065, 0.09, 0.62,
            0.6, false, 0.34, 9F, MountFlight.wingSound(), 0.85F, 20);
    private static final double BREATH_RANGE = 9.0;
    private static final double BREATH_COS = Math.cos(Math.toRadians(28.0));

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.wyvern.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.wyvern.walk");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.wyvern.fly");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.wyvern.glide");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.wyvern.sit");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.wyvern.attack");
    private static final RawAnimation BREATH = RawAnimation.begin().thenPlay("animation.wyvern.breath");
    private static final RawAnimation ROAR = RawAnimation.begin().thenPlay("animation.wyvern.roar");

    /** Ticks de calma depois de comer carne podre (selvagem). */
    private int calmTicks;
    @Nullable
    private UUID breathTarget;

    public Wyvern(EntityType<? extends Wyvern> type, Level level) {
        super(type, level, PARAMS);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 70.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AnswerCallGoal<>(this, 1.5));
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(3, new AerialAttackGoal<>(this, 1.15, 26));
        goalSelector.addGoal(4, new FlyingFollowOwnerGoal(this, 1.1, 12F, 4F));
        goalSelector.addGoal(5, new BreedGoal(this, 1.0));
        goalSelector.addGoal(6, new SoarGoal<>(this, 500));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 14F));
        goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isHostileTo));
    }

    /** Selvagem e não acalmada ataca jogadores (nunca no Pacífico). */
    private boolean isHostileTo(LivingEntity e) {
        return !isTame() && calmTicks <= 0 && level().getDifficulty() != Difficulty.PEACEFUL;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !isTame();
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return !isTame();
    }

    // =====================================================================================
    //  Doma: carne podre (acalma) + Essência Sombria (doma)
    // =====================================================================================

    private static boolean isShadowEssence(ItemStack stack) {
        return stack.is(CoreRegistries.ESSENCES.get(Element.SHADOW).get());
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH);
    }

    @Override
    protected boolean isHealingFood(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH) || stack.is(ItemTags.MEAT);
    }

    @Override
    protected InteractionResult interactWild(Player player, InteractionHand hand, ItemStack stack) {
        boolean client = level().isClientSide;
        if (stack.is(Items.ROTTEN_FLESH) && !isBaby()) {
            if (!client) {
                stack.consume(1, player);
                calmTicks = 600;
                setTarget(null);
                setLastHurtByMob(null);
                navigation.stop();
                heal(4.0F);
                playSound(SoundEvents.GENERIC_EAT, 1.0F, 0.6F);
                triggerAnim("action", "roar");
                MountFx.burst(this, ParticleTypes.SMOKE, 12);
                player.displayClientMessage(Component.translatable("message.isekai_mahou.wyvern_calmed"), true);
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (isShadowEssence(stack) && !isBaby()) {
            if (!client) {
                if (calmTicks <= 0) {
                    player.displayClientMessage(Component.translatable("message.isekai_mahou.wyvern_needs_calm"), true);
                } else {
                    stack.consume(1, player);
                    if (level() instanceof ServerLevel sl) ElementFx.burst(sl, position().add(0, 1.2, 0), Element.SHADOW, 14, 0.8);
                    playSound(SoundEvents.ENDER_DRAGON_GROWL, 0.5F, 1.5F);
                    if (!tryTame(player, 3)) {
                        player.displayClientMessage(Component.translatable("message.isekai_mahou.wyvern_distrust"), true);
                    } else {
                        calmTicks = 0;
                    }
                }
            }
            return InteractionResult.sidedSuccess(client);
        }
        return InteractionResult.PASS;
    }

    @Override
    protected double wildMaxHealth() {
        return 70.0;
    }

    @Override
    protected double tamedMaxHealth() {
        return 95.0;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof LivingEntity) calmTicks = 0;
        return hurt;
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (target instanceof net.minecraft.world.entity.monster.Creeper) return false;
        if (target instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame() && t.getOwner() == owner) return false;
        return !(target instanceof Player p && owner instanceof Player o && !o.canHarmPlayer(p));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (calmTicks > 0) {
            calmTicks--;
            if (getTarget() instanceof Player) setTarget(null);
            if (calmTicks % 20 == 0 && level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 2.0, getZ(), 2, 0.3, 0.2, 0.3, 0.01);
            }
        }
    }

    // =====================================================================================
    //  Habilidade: Sopro Sombrio
    // =====================================================================================

    @Override
    public String abilityId() {
        return "wyvern_breath";
    }

    @Override
    public int abilityCooldownMax() {
        return 160;
    }

    @Override
    protected int abilityDuration() {
        return 40;
    }

    @Override
    public double flightSpeedMultiplier() {
        return abilityActive() > 0 ? 0.55 : 1.0;
    }

    @Override
    protected boolean startAbility(@Nullable LivingEntity rider, @Nullable LivingEntity target) {
        breathTarget = target != null ? target.getUUID() : null;
        triggerAnim("action", "breath");
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_GROWL, getSoundSource(), 1.2F, 1.35F);
        return true;
    }

    /** IA selvagem: de média distância, para no ar e sopra no alvo. */
    @Override
    public boolean rangedAttackTick(LivingEntity target, double distSqr) {
        if (abilityActive() > 0) {
            getLookControl().setLookAt(target, 30F, 30F);
            getMoveControl().setWantedPosition(getX(), getY(), getZ(), 0.5); // paira enquanto sopra
            return true;
        }
        if (distSqr > 12.0 && distSqr < 64.0 && hasLineOfSight(target) && random.nextInt(25) == 0) {
            return tryWildAbility(target);
        }
        return false;
    }

    /** Boca: à frente da cabeça (aprox.), na direção do corpo. */
    private Vec3 mouthPos() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        return position().add(fwd.scale(getBbWidth() * 0.5 + 1.3)).add(0, 1.35, 0);
    }

    @Override
    protected void abilityTick(int ticksLeft) {
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 origin = mouthPos();
        LivingEntity rider = getControllingPassenger();
        Vec3 dir;
        if (rider != null) {
            dir = rider.getLookAngle();
        } else if (breathTarget != null && sl.getEntity(breathTarget) instanceof LivingEntity t && t.isAlive()) {
            dir = t.getEyePosition().subtract(origin).normalize();
        } else {
            dir = getLookAngle();
        }

        // Partículas: cone de chamas sombrias
        for (int i = 0; i < 7; i++) {
            double d = 0.6 + random.nextDouble() * BREATH_RANGE;
            double spread = d * 0.28;
            Vec3 p = origin.add(dir.scale(d)).add((random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.5) * spread,
                    (random.nextDouble() - 0.5) * spread);
            sl.sendParticles(ParticleTypes.DRAGON_BREATH, p.x, p.y, p.z, 1, 0, 0, 0, 0.01);
            if (i % 2 == 0) sl.sendParticles(ElementFx.dust(Element.SHADOW, 1.6F), p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0);
            if (i == 0) sl.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.02);
        }
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.x, origin.y, origin.z, 2, 0.1, 0.1, 0.1, 0.03);
        if (ticksLeft % 6 == 0) playSound(SoundEvents.FIRECHARGE_USE, 0.6F, 0.55F + random.nextFloat() * 0.1F);

        // Dano a cada 10 ticks: casa com a invulnerabilidade pós-golpe (golpes mais frequentes seriam ignorados)
        if (ticksLeft % 10 != 0) return;
        AABB box = new AABB(origin, origin.add(dir.scale(BREATH_RANGE))).inflate(3.0);
        // Montada é a arma do cavaleiro (mais forte); selvagem é um pouco mais branda, já que ignora armadura
        float damage = rider != null ? 5.0F : 3.5F;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box)) {
            if (MountFx.isFriendly(this, e, rider)) continue;
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(origin);
            double len = to.length();
            if (len > BREATH_RANGE + e.getBbWidth() || len < 1.0E-3) continue;
            if (to.scale(1.0 / len).dot(dir) < BREATH_COS) continue;
            if (!hasLineOfSight(e)) continue; // não atravessa paredes
            var src = damageSources().indirectMagic(this, rider != null ? rider : this);
            if (e.hurt(src, damage)) {
                e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 80, 0), this);
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), this);
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
            playSound(SoundEvents.RAVAGER_ATTACK, 0.9F, 1.1F);
            if (target instanceof LivingEntity le) le.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 60, 0), this);
        }
        return hit;
    }

    @Override
    protected void playFlapSound() {
        playSound(flight.params().flapSound(), 0.8F, flight.params().flapPitch() + random.nextFloat() * 0.1F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return random.nextInt(4) == 0 ? SoundEvents.ENDER_DRAGON_GROWL : SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 0.4F, 1.2F);
    }

    @Override
    protected float getSoundVolume() {
        return 0.7F;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.25F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Calm", calmTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        calmTicks = tag.getInt("Calm");
    }

    // =====================================================================================
    //  Animações
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> {
            if (isInSittingPose()) return state.setAndContinue(SIT);
            if (flight.isAirborne()) return state.setAndContinue(flight.isGliding() ? GLIDE : FLY);
            if (state.isMoving()) return state.setAndContinue(WALK);
            return state.setAndContinue(IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("breath", BREATH)
                .triggerableAnim("roar", ROAR));
    }
}
