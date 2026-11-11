package com.renanpontes.isekaimahou.feature.mounts.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.mounts.entity.goal.AirWanderGoal;
import com.renanpontes.isekaimahou.feature.mounts.entity.goal.AnswerCallGoal;
import com.renanpontes.isekaimahou.feature.mounts.entity.goal.FlyingFollowOwnerGoal;
import com.renanpontes.isekaimahou.feature.mounts.entity.goal.PanicFlightGoal;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Arraia do Céu: manta gigante que "nada" no ar do Plano Elemental, sempre flutuando (nunca pousa).
 * Pacífica. Domada com Essências (qualquer uma, 1 em 4; a de Vento, 1 em 2); cura com algas.
 * Montada, desliza seguindo o olhar em 3D, como se nadasse; parada, flutua.
 * Habilidade "Corrente Celeste": dispara numa corrente de vento e dá Queda Lenta ao cavaleiro.
 */
public class SkyRay extends AbstractSkyMount {
    public static final String ID = "sky_ray";
    private static final MountFlight.Params PARAMS = new MountFlight.Params(0.62, 0.3, 0.34, 0.0, 0.07, 0.08, 0.2,
            1.0, true, 0.2, 7F, SoundEvents.PHANTOM_FLAP, 0.6F, 30);

    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("animation.sky_ray.swim");
    private static final RawAnimation SWIM_FAST = RawAnimation.begin().thenLoop("animation.sky_ray.swim_fast");
    private static final RawAnimation REST = RawAnimation.begin().thenLoop("animation.sky_ray.rest");
    private static final RawAnimation SURGE = RawAnimation.begin().thenPlay("animation.sky_ray.surge");

    public SkyRay(EntityType<? extends SkyRay> type, Level level) {
        super(type, level, PARAMS);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 34.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.FLYING_SPEED, 0.32)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public boolean alwaysFlying() {
        return true;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new AnswerCallGoal<>(this, 1.6));
        goalSelector.addGoal(1, new PanicFlightGoal<>(this, 1.6)); // selvagem e ferida: foge para o alto
        goalSelector.addGoal(2, new FlyingFollowOwnerGoal(this, 1.2, 10F, 4F));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(4, new TemptGoal(this, 1.1, SkyRay::isEssence, false));
        goalSelector.addGoal(5, new AirWanderGoal<>(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8F));
    }

    // =====================================================================================
    //  Doma
    // =====================================================================================

    /** Qualquer Essência do núcleo (arcana + 6 elementos). */
    public static boolean isEssence(ItemStack stack) {
        for (var item : CoreRegistries.ESSENCES.values()) {
            if (stack.is(item.get())) return true;
        }
        return false;
    }

    private static boolean isWindEssence(ItemStack stack) {
        Item wind = CoreRegistries.ESSENCES.get(Element.WIND).get();
        return stack.is(wind);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return isWindEssence(stack);
    }

    @Override
    protected boolean isHealingFood(ItemStack stack) {
        return stack.is(Items.KELP) || stack.is(Items.DRIED_KELP) || stack.is(Items.SEAGRASS);
    }

    @Override
    protected float healAmount(ItemStack stack) {
        return stack.is(Items.DRIED_KELP) ? 6.0F : 4.0F;
    }

    @Override
    protected InteractionResult interactWild(Player player, InteractionHand hand, ItemStack stack) {
        if (!isEssence(stack) || isBaby()) return InteractionResult.PASS;
        boolean client = level().isClientSide;
        if (!client) {
            stack.consume(1, player);
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 1.4F);
            if (level() instanceof ServerLevel sl) ElementFx.burst(sl, position().add(0, 0.5, 0), Element.WIND, 10, 0.6);
            tryTame(player, isWindEssence(stack) ? 2 : 4);
        }
        return InteractionResult.sidedSuccess(client);
    }

    @Override
    protected double wildMaxHealth() {
        return 34.0;
    }

    @Override
    protected double tamedMaxHealth() {
        return 46.0;
    }

    // =====================================================================================
    //  Habilidade: Corrente Celeste
    // =====================================================================================

    @Override
    public String abilityId() {
        return "sky_ray_current";
    }

    @Override
    public int abilityCooldownMax() {
        return 300;
    }

    @Override
    protected int abilityDuration() {
        return 100;
    }

    @Override
    public double flightSpeedMultiplier() {
        return abilityActive() > 0 ? 1.9 : 1.0;
    }

    @Override
    protected boolean startAbility(@Nullable LivingEntity rider, @Nullable LivingEntity target) {
        if (rider == null) return false;
        rider.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 300, 0, false, true));
        triggerAnim("action", "surge");
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), getSoundSource(), 1.2F, 0.7F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, getSoundSource(), 1.0F, 1.6F);
        if (level() instanceof ServerLevel sl) ElementFx.ring(sl, position().add(0, 0.4, 0), Element.WIND, 2.5, 24);
        return true;
    }

    @Override
    protected void abilityTick(int ticksLeft) {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 2, 0.8, 0.1, 0.8, 0.01);
            sl.sendParticles(ElementFx.dust(Element.WIND, 1.2F), getX(), getY() + 0.3, getZ(), 3, 1.2, 0.15, 1.2, 0.0);
        }
    }

    // =====================================================================================
    //  Física e sons
    // =====================================================================================

    @Override
    protected void playFlapSound() {
        playSound(flight.params().flapSound(), 0.35F, flight.params().flapPitch() + random.nextFloat() * 0.1F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return random.nextBoolean() ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.DOLPHIN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.DOLPHIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.DOLPHIN_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    /** Brilho suave das pintas no céu do Plano Elemental. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && random.nextInt(6) == 0) {
            level().addParticle(ParticleTypes.END_ROD, getRandomX(0.8), getY() + 0.35, getRandomZ(0.8), 0, -0.01, 0);
        }
    }

    // =====================================================================================
    //  Animações
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 8, state -> {
            double speed = flight.horizontalSpeed();
            if (abilityActive() > 0 || speed > 0.3) return state.setAndContinue(SWIM_FAST);
            if (speed > 0.03 || Math.abs(flight.verticalSpeed()) > 0.03) return state.setAndContinue(SWIM);
            return state.setAndContinue(REST);
        }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("surge", SURGE));
    }
}
