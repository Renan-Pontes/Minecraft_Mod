package com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental;

import java.util.List;

import com.renanpontes.isekaimahou.feature.creatures_e.CeFx;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.BoltKind;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.CreatureMove;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalBolt;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalCreature;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Fenix Menor (Ilhas de Brasa): ave de fogo que mergulha sobre a presa e dispara dardos de chamas.
 * Renasce UMA vez: ao "morrer" vira um casulo de brasas por alguns segundos e volta com 60% da vida,
 * explodindo em chamas.
 */
public class LesserPhoenix extends DimensionalCreature implements Enemy {
    private static final EntityDataAccessor<Integer> DATA_REBIRTH = SynchedEntityData.defineId(LesserPhoenix.class, EntityDataSerializers.INT);
    private static final int REBIRTH_TICKS = 60;

    private boolean reborn;
    private boolean diveHit;

    public LesserPhoenix(EntityType<? extends LesserPhoenix> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(60, 4, 9, 0.3)
                .add(Attributes.FLYING_SPEED, 0.2)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    public Faction faction() {
        return Faction.ELEMENTAL;
    }

    @Override
    protected int experience() {
        return 30;
    }

    @Override
    public boolean flies() {
        return true;
    }

    @Override
    public double preferredRange() {
        return 4.0;
    }

    @Override
    public double hoverHeight() {
        return 2.5;
    }

    @Override
    public double chaseSpeed() {
        return 1.3;
    }

    @Override
    protected List<String> extraAnimations() {
        return List.of("rebirth");
    }

    @Override
    protected List<CreatureMove> createMoves() {
        return List.of(
                CreatureMove.of("dive").timing(8, 30).cooldown(90).range(3.0, 16).mobile()
                        .onStart(t -> {
                            diveHit = false;
                            CeFx.sound(level(), position(), SoundEvents.PHANTOM_SWOOP, 1.2F, 1.3F);
                        })
                        .during(this::diveTick),
                CreatureMove.of("attack").timing(6, 12).cooldown(22).range(0, 2.0).perform(this::talons),
                CreatureMove.of("cast").timing(10, 22).cooldown(50).range(2.5, 20)
                        .during((t, i) -> {
                            if (i == 10 || i == 16) firebolt(t);
                        }));
    }

    private void talons(LivingEntity t) {
        if (!(level() instanceof ServerLevel level) || gapTo(t) > 2.8) return;
        if (meleeHit(t, 1.0F, 0.4)) t.igniteForSeconds(5);
        CeFx.burst(level, center(t), ParticleTypes.FLAME, 14, 0.3, 0.06);
        CeFx.sound(level, center(t), SoundEvents.PHANTOM_BITE, 1.0F, 1.4F);
    }

    private void firebolt(LivingEntity t) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 from = position().add(0, 0.6, 0).add(getLookAngle().scale(0.7));
        DimensionalBolt.fire(this, BoltKind.PHOENIX, from, center(t), 1.0F, 6.0F, 4.0F).homing(t);
        CeFx.sound(level, from, SoundEvents.BLAZE_SHOOT, 0.8F, 1.6F);
    }

    /** Mergulho: dispara contra o alvo deixando um rastro de fogo; o primeiro toque fere e incendeia. */
    private void diveTick(LivingEntity t, int i) {
        if (!(level() instanceof ServerLevel level) || i < 8) return;
        if (!diveHit && i < 26) dashAt(t, 0.9, 0.0);
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.5, getZ(), 4, 0.25, 0.25, 0.25, 0.01);
        if (!diveHit && touching(t, 0.4)) {
            diveHit = true;
            if (meleeHit(t, 1.3F, 1.0)) t.igniteForSeconds(6);
            CeFx.burst(level, center(t), ParticleTypes.FLAME, 30, 0.4, 0.1);
            CeFx.sound(level, center(t), SoundEvents.FIRECHARGE_USE, 1.0F, 1.2F);
            setDeltaMovement(getDeltaMovement().scale(-0.4).add(0, 0.5, 0));
            endMove();
        }
    }

    // =====================================================================================
    //  Renascimento
    // =====================================================================================

    public boolean isRebirthing() {
        return entityData.get(DATA_REBIRTH) > 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_REBIRTH, 0);
    }

    @Override
    public void die(DamageSource source) {
        boolean canRevive = !reborn && !isRemoved() && !source.is(DamageTypes.GENERIC_KILL)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && level() instanceof ServerLevel;
        if (!canRevive) {
            super.die(source);
            return;
        }
        reborn = true;
        endMove();
        setHealth(1.0F);
        clearFire();
        removeAllEffects();
        entityData.set(DATA_REBIRTH, REBIRTH_TICKS);
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        playAction("rebirth");
        ServerLevel level = (ServerLevel) level();
        CeFx.burst(level, position().add(0, 0.5, 0), ParticleTypes.LARGE_SMOKE, 30, 0.4, 0.03);
        CeFx.sound(level, position(), SoundEvents.BLAZE_DEATH, 1.0F, 1.4F);
        CeFx.sound(level, position(), SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.6F);
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || isRebirthing();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isRebirthing() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();
        int r = entityData.get(DATA_REBIRTH);
        if (r <= 0) return;
        setDeltaMovement(getDeltaMovement().scale(0.5));
        if (level() instanceof ServerLevel level) {
            entityData.set(DATA_REBIRTH, r - 1);
            if (r % 10 == 0) CeFx.sound(level, position(), SoundEvents.FIRE_AMBIENT, 1.0F, 0.8F + (REBIRTH_TICKS - r) * 0.01F);
            if (r == 1) rise(level);
        } else {
            // casulo de brasas: chamas espiralando para dentro
            double a = tickCount * 0.6;
            double rad = 0.4 + r / (double) REBIRTH_TICKS * 1.2;
            for (int k = 0; k < 3; k++) {
                double ang = a + k * Math.PI * 2 / 3;
                level().addParticle(ParticleTypes.FLAME, getX() + Math.cos(ang) * rad, getY() + 0.5 + Math.sin(a + k) * 0.4,
                        getZ() + Math.sin(ang) * rad, 0, 0.01, 0);
            }
            level().addParticle(ParticleTypes.LAVA, getX(), getY() + 0.5, getZ(), 0, 0, 0);
        }
    }

    /** Volta a vida: explosao de chamas que fere e empurra quem estiver perto. */
    private void rise(ServerLevel level) {
        setHealth(getMaxHealth() * 0.6F);
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 400, 0));
        Vec3 c = position().add(0, 0.5, 0);
        CeFx.shockwave(level, c, ParticleTypes.FLAME, 60, 0.5);
        CeFx.burst(level, c, ParticleTypes.TOTEM_OF_UNDYING, 40, 0.5, 0.4);
        CeFx.burst(level, c, CeFx.dust(0xFFD23F, 1.8F), 30, 0.8, 0.0);
        CeFx.sound(level, c, SoundEvents.TOTEM_USE, 1.0F, 1.2F);
        CeFx.sound(level, c, SoundEvents.BLAZE_SHOOT, 1.4F, 0.6F);
        for (LivingEntity e : foesNear(c, 4.5)) {
            if (strike(e, 7.0F, magicSource(this))) e.igniteForSeconds(6);
            e.knockback(1.0, getX() - e.getX(), getZ() - e.getZ());
            e.hurtMarked = true;
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || isRebirthing()) return;
        if (tickCount % 60 == 0 && getHealth() < getMaxHealth()) heal(1.0F);
        if (tickCount % 14 == 0 && getDeltaMovement().lengthSqr() > 0.01) playSound(SoundEvents.PHANTOM_FLAP, 0.5F, 1.5F);
    }

    @Override
    public boolean isSensitiveToWater() {
        return true;
    }

    @Override
    protected void clientFx() {
        var r = random;
        if (isRebirthing()) return;
        level().addParticle(ParticleTypes.FLAME, getRandomX(0.6), getY() + 0.3 + r.nextDouble() * 0.6, getRandomZ(0.6), 0, 0.01, 0);
        if (r.nextInt(2) == 0) level().addParticle(CeFx.dust(r.nextBoolean() ? 0xFFD23F : 0xFF6A2B, 0.8F), getRandomX(0.8), getY() + 0.5, getRandomZ(0.8), 0, -0.02, 0);
        if (r.nextInt(5) == 0) level().addParticle(ParticleTypes.SMALL_FLAME, getRandomX(0.4), getY() + 0.1, getRandomZ(0.4), 0, -0.03, 0);
    }

    @Override
    protected void deathFx(ServerLevel level) {
        Vec3 c = position().add(0, 0.5, 0);
        CeFx.burst(level, c, ParticleTypes.FLAME, 40, 0.5, 0.08);
        CeFx.burst(level, c, ParticleTypes.WHITE_ASH, 40, 0.6, 0.02);
        CeFx.burst(level, c, ParticleTypes.LARGE_SMOKE, 10, 0.4, 0.03);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Reborn", reborn);
        // casulo em andamento sobrevive a salvar/descarregar (senao voltaria com 1 de vida para sempre)
        tag.putInt("RebirthTicks", entityData.get(DATA_REBIRTH));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        reborn = tag.getBoolean("Reborn");
        entityData.set(DATA_REBIRTH, Math.max(0, Math.min(REBIRTH_TICKS, tag.getInt("RebirthTicks"))));
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.5F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BLAZE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLAZE_DEATH;
    }
}
