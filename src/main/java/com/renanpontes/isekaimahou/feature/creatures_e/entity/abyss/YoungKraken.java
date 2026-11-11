package com.renanpontes.isekaimahou.feature.creatures_e.entity.abyss;

import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.creatures_e.CeFx;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.BoltKind;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.CreatureMove;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalBolt;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalCreature;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Kraken Jovem (Profundezas Abissais): cefalopode anfibio. Chicoteia com os tentaculos (e puxa), agarra e
 * esmaga a presa, solta uma nuvem de tinta quando acuado e dispara jatos d'agua quando esta na agua.
 * Um golpe forte (8+ de dano) faz ele soltar quem esta agarrado.
 */
public class YoungKraken extends DimensionalCreature implements Enemy {
    private LivingEntity grabbed;

    public YoungKraken(EntityType<? extends YoungKraken> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(95, 6, 10, 0.22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public Faction faction() {
        return Faction.ABYSS;
    }

    @Override
    protected int experience() {
        return 30;
    }

    @Override
    public boolean swims() {
        return true;
    }

    @Override
    protected boolean immuneToToxins() {
        return false;
    }

    @Override
    public double preferredRange() {
        return 1.2;
    }

    @Override
    protected List<CreatureMove> createMoves() {
        return List.of(
                CreatureMove.of("ink").timing(6, 12).cooldown(240).range(0, 8).blind()
                        .when(t -> getHealth() < getMaxHealth() * 0.6F || foesNear(position(), 4).size() >= 2)
                        .perform(t -> ink()),
                CreatureMove.of("grab").timing(8, 58).cooldown(160).range(0, 2.4)
                        .perform(this::grab)
                        .during(this::squeeze),
                CreatureMove.of("attack").timing(8, 14).cooldown(22).range(0, 3.2).perform(this::lash),
                CreatureMove.of("cast").timing(10, 18).cooldown(60).range(3.5, 18).when(t -> isInWater()).perform(this::jet));
    }

    /** Chicotada com alcance longo: fere e puxa a vitima para perto. */
    private void lash(LivingEntity t) {
        if (!(level() instanceof ServerLevel level) || gapTo(t) > 4.0) return;
        meleeHit(t, 1.0F, 0.0);
        Vec3 pull = position().subtract(t.position()).normalize().scale(0.55);
        t.push(pull.x, 0.15, pull.z);
        t.hurtMarked = true;
        CeFx.burst(level, center(t), ParticleTypes.SPLASH, 16, 0.3, 0.1);
        CeFx.sound(level, center(t), SoundEvents.SLIME_ATTACK, 1.0F, 0.5F);
    }

    private void grab(LivingEntity t) {
        if (gapTo(t) > 3.2) {
            endMove();
            return;
        }
        grabbed = t;
        t.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, 50, 0), this);
        CeFx.sound(level(), position(), SoundEvents.SQUID_SQUIRT, 1.0F, 0.5F);
    }

    /** Esmaga a vitima agarrada; no fim a arremessa. */
    private void squeeze(LivingEntity t, int i) {
        if (!(level() instanceof ServerLevel level) || i < 8 || grabbed == null) return;
        if (!grabbed.isAlive() || distanceTo(grabbed) > 5.0) {
            release();
            endMove();
            return;
        }
        Vec3 hold = position().add(getLookAngle().multiply(1, 0, 1).normalize().scale(getBbWidth() * 0.5 + 0.6)).add(0, 0.6, 0);
        Vec3 v = hold.subtract(grabbed.position()).scale(0.25);
        grabbed.setDeltaMovement(v);
        grabbed.hurtMarked = true;
        if (i % 10 == 0) {
            strike(grabbed, 3.0F, damageSources().mobAttack(this));
            CeFx.burst(level, center(grabbed), ParticleTypes.SQUID_INK, 6, 0.2, 0.02);
            CeFx.sound(level, center(grabbed), SoundEvents.SLIME_SQUISH, 1.0F, 0.6F);
        }
        if (i >= 57) {
            Vec3 throwDir = getLookAngle().multiply(1, 0, 1).normalize();
            grabbed.push(throwDir.x * 1.2, 0.6, throwDir.z * 1.2);
            grabbed.hurtMarked = true;
            release();
        }
    }

    private void release() {
        if (grabbed != null) grabbed.removeEffect(CoreRegistries.ROOTED);
        grabbed = null;
    }

    private void ink() {
        if (!(level() instanceof ServerLevel level)) return;
        AreaEffectCloud cloud = new AreaEffectCloud(level, getX(), getY() + 0.2, getZ());
        cloud.setOwner(this);
        cloud.setParticle(ParticleTypes.SQUID_INK);
        cloud.setRadius(4.0F);
        cloud.setRadiusPerTick(-0.02F);
        cloud.setDuration(100);
        cloud.setWaitTime(0);
        cloud.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
        cloud.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
        level.addFreshEntity(cloud);
        CeFx.burst(level, position().add(0, 0.8, 0), ParticleTypes.SQUID_INK, 80, 1.2, 0.1);
        CeFx.burst(level, position().add(0, 0.8, 0), ParticleTypes.GLOW_SQUID_INK, 10, 1.0, 0.05);
        CeFx.sound(level, position(), SoundEvents.SQUID_SQUIRT, 1.5F, 0.5F);
    }

    private void jet(LivingEntity t) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 from = position().add(0, 1.0, 0).add(getLookAngle().scale(0.9));
        DimensionalBolt.fire(this, BoltKind.WATER, from, center(t), 1.3F, 7.0F, 2.0F);
        CeFx.sound(level, from, SoundEvents.GLOW_SQUID_SQUIRT, 1.0F, 0.6F);
    }

    @Override
    protected void onHurtBy(LivingEntity attacker, DamageSource source, float amount) {
        // pancada forte faz soltar a presa
        if (grabbed != null && amount >= 8.0F) {
            release();
            if ("grab".equals(actionName())) endMove();
        }
    }

    @Override
    public void endMove() {
        release();
        super.endMove();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && tickCount % 30 == 0 && isInWater() && getHealth() < getMaxHealth()) heal(1.0F);
    }

    @Override
    protected void clientFx() {
        var r = random;
        if (isInWater()) {
            if (r.nextInt(3) == 0) level().addParticle(ParticleTypes.BUBBLE, getRandomX(0.7), getY() + r.nextDouble() * 1.2, getRandomZ(0.7), 0, 0.05, 0);
            if (r.nextInt(10) == 0) level().addParticle(ParticleTypes.GLOW, getRandomX(0.8), getY() + r.nextDouble(), getRandomZ(0.8), 0, 0.01, 0);
        } else if (r.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.DRIPPING_WATER, getRandomX(0.7), getY() + 0.4 + r.nextDouble() * 0.8, getRandomZ(0.7), 0, 0, 0);
        }
    }

    @Override
    protected void deathFx(ServerLevel level) {
        Vec3 c = position().add(0, 0.7, 0);
        CeFx.burst(level, c, ParticleTypes.SQUID_INK, 60, 0.8, 0.05);
        CeFx.burst(level, c, ParticleTypes.GLOW_SQUID_INK, 15, 0.6, 0.03);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.GLOW_SQUID_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SQUID_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SQUID_DEATH;
    }
}
