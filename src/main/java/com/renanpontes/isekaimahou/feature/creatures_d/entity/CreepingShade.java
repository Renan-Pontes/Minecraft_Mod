package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ai.AbilityGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RestrictSunGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Sombra Rastejante: predador das florestas escuras à noite. No escuro fica quase invisível e
 * silenciosa; dá botes, morde corrompendo e solta um véu de trevas que cega quem estiver perto.
 * A luz do sol a dissolve aos poucos, então ela evita caminhos ensolarados.
 */
public class CreepingShade extends ElementalCreature implements Enemy {
    private static final EntityDataAccessor<Boolean> STEALTH = SynchedEntityData.defineId(CreepingShade.class, EntityDataSerializers.BOOLEAN);
    private int veilTicks;
    private float alpha = 0.9F;
    private float alphaO = 0.9F;

    public CreepingShade(EntityType<? extends CreepingShade> type, Level level) {
        super(type, level);
        this.xpReward = 9;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 22.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public Element element() {
        return Element.SHADOW;
    }

    @Override
    protected String[] actionAnimations() {
        return new String[] {"attack", "special", "cast"};
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STEALTH, false);
    }

    public boolean isStealthed() {
        return entityData.get(STEALTH);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new RestrictSunGoal(this));
        goalSelector.addGoal(2, new AbilityGoal(this, "special", 3, 7, 10, 80, 120, true, this::pounce)
                .when(t -> onGround())
                .windup((t, left) -> {
                    if (left % 3 == 0) serverParticles(ParticleTypes.SMOKE, position().add(0, 0.3, 0), 4, 0.4, 0.01);
                }));
        goalSelector.addGoal(3, new AbilityGoal(this, "cast", 0, 10, 15, 220, 320, true, t -> veil())
                .windup((t, left) -> {
                    if (level() instanceof ServerLevel sl && left % 3 == 0) MobMagic.converge(sl, position().add(0, 0.6, 0), ParticleTypes.SQUID_INK, 2.5, 3);
                }));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.25, false));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** Bote: salta sobre o alvo (o golpe sai ao encostar, pelo ataque corpo a corpo). */
    private void pounce(LivingEntity target) {
        MobMagic.aim(this, target);
        Vec3 d = target.position().subtract(position()).multiply(1, 0, 1);
        if (d.lengthSqr() < 1.0E-4) return;
        d = d.normalize().scale(Math.min(1.3, 0.25 + d.length() * 0.18));
        setDeltaMovement(d.x, 0.45, d.z);
        hasImpulse = true;
        playSoundHere(SoundEvents.PHANTOM_SWOOP, 1.0F, 0.6F);
    }

    /** Véu de trevas: cega quem está perto e a esconde mesmo na luz por alguns segundos. */
    private void veil() {
        if (!(level() instanceof ServerLevel sl)) return;
        for (LivingEntity e : MobMagic.victimsAround(this, position(), 10.0)) {
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), this);
            if (e instanceof Player) e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0), this);
        }
        veilTicks = 80;
        entityData.set(STEALTH, true);
        sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.5, getZ(), 60, 2.0, 0.6, 2.0, 0.05);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 30, 1.5, 0.4, 1.5, 0.02);
        playSoundHere(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.7F, 0.6F);
    }

    @Override
    protected void onMeleeHit(LivingEntity target) {
        target.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 60, 0), this);
        if (target instanceof Player) target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), this);
        veilTicks = 0;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            alphaO = alpha;
            float want = isStealthed() ? 0.22F : 0.92F;
            alpha += (want - alpha) * 0.15F;
            return;
        }
        if (!isAlive()) return;
        if (veilTicks > 0) veilTicks--;
        if (tickCount % 10 == 0) {
            boolean dark = level().getMaxLocalRawBrightness(blockPosition()) <= 6;
            boolean stealth = veilTicks > 0 || (dark && !isAggressiveNow());
            if (stealth != isStealthed()) entityData.set(STEALTH, stealth);
        }
        // A luz do sol a desfaz (a chuva e a água protegem)
        if (tickCount % 20 == 0 && level().isDay() && !isInWaterRainOrBubble()
                && level().canSeeSky(BlockPos.containing(getX(), getEyeY(), getZ())) && getLightLevelDependentMagicValue() > 0.5F) {
            hurt(damageSources().magic(), 2.0F);
            serverParticles(ParticleTypes.LARGE_SMOKE, position().add(0, 0.5, 0), 8, 0.4, 0.02);
        }
    }

    /** Fica visível no instante do bote/mordida (para o jogador ter chance de ver o ataque). */
    private boolean isAggressiveNow() {
        return getTarget() != null && distanceToSqr(getTarget()) < 3.0 * 3.0;
    }

    @Override
    public float renderAlpha(float partialTick) {
        return Mth.lerp(partialTick, alphaO, alpha);
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return true;
    }

    @Override
    protected void clientEffects() {
        int chance = isStealthed() ? 8 : 3;
        if (random.nextInt(chance) == 0) bodyParticle(ParticleTypes.SMOKE, 0.6, 0.5, 0.01);
        if (random.nextInt(chance * 2) == 0) bodyParticle(ElementFx.dust(Element.SHADOW, 0.8F), 0.5, 0.6, 0);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // Silenciosa: não faz barulho ao andar
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SOUL_ESCAPE.value();
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.55F;
    }
}
