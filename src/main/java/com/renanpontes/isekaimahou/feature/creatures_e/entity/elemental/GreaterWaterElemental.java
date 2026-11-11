package com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental;

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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Elemental de Agua Maior (Mar Suspenso): corpo de agua viva, anfibio. Golpes que encharcam, jato d'agua
 * e um Redemoinho que puxa todos para perto. Esta sempre Encharcado (Raio o eletrocuta) e se regenera na agua.
 */
public class GreaterWaterElemental extends DimensionalCreature implements Enemy {

    public GreaterWaterElemental(EntityType<? extends GreaterWaterElemental> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(85, 6, 10, 0.25)
                .add(Attributes.ARMOR_TOUGHNESS, 1)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public Faction faction() {
        return Faction.ELEMENTAL;
    }

    @Override
    protected int experience() {
        return 25;
    }

    @Override
    public boolean swims() {
        return true;
    }

    @Override
    public double preferredRange() {
        return 4.0;
    }

    @Override
    protected List<CreatureMove> createMoves() {
        return List.of(
                CreatureMove.of("whirlpool").timing(10, 60).cooldown(220).range(0, 6.5).blind()
                        .onStart(t -> CeFx.sound(level(), position(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.6F, 0.7F))
                        .during(this::whirlpool),
                CreatureMove.of("attack").timing(9, 18).cooldown(24).range(0, 2.3).perform(this::slam),
                CreatureMove.of("cast").timing(10, 20).cooldown(45).range(2.5, 20).perform(this::jet));
    }

    private void slam(LivingEntity t) {
        if (!(level() instanceof ServerLevel level) || gapTo(t) > 3.0) return;
        meleeHit(t, 1.0F, 1.2);
        t.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 140, 0), this);
        Vec3 at = center(t);
        CeFx.burst(level, at, ParticleTypes.SPLASH, 40, 0.5, 0.25);
        CeFx.burst(level, at, ParticleTypes.BUBBLE_POP, 12, 0.4, 0.05);
        CeFx.sound(level, at, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.0F, 0.8F);
    }

    private void jet(LivingEntity t) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 from = position().add(0, 2.0, 0).add(getLookAngle().scale(0.8));
        DimensionalBolt.fire(this, BoltKind.WATER, from, center(t), 1.4F, 8.0F, 1.5F);
        CeFx.burst(level, from, ParticleTypes.SPLASH, 16, 0.2, 0.15);
        CeFx.sound(level, from, SoundEvents.DOLPHIN_SPLASH, 1.0F, 0.6F);
    }

    /** Redemoinho: puxa os inimigos em espiral, fere e encharca; termina numa onda que os arremessa. */
    private void whirlpool(LivingEntity target, int i) {
        if (!(level() instanceof ServerLevel level) || i < 10) return;
        Vec3 c = position().add(0, 0.3, 0);
        double phase = i * 0.45;
        for (int k = 0; k < 3; k++) {
            double r = 6.5 - ((i * 0.2 + k * 2.2) % 6.0);
            double a = phase + k * 2.1;
            level.sendParticles(ParticleTypes.SPLASH, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 3, 0.1, 0.05, 0.1, 0.05);
            level.sendParticles(CeFx.dust(0x3FA9FF, 1.4F), c.x + Math.cos(a) * r, c.y + 0.4, c.z + Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0);
            level.sendParticles(ParticleTypes.BUBBLE_POP, c.x + Math.cos(a + 1) * r, c.y + 0.3, c.z + Math.sin(a + 1) * r, 1, 0, 0, 0, 0);
        }
        for (LivingEntity e : foesNear(c, 8.0)) {
            Vec3 to = c.subtract(e.position()).multiply(1, 0, 1);
            double d = Math.max(0.5, to.length());
            Vec3 pull = to.normalize().scale(0.09);
            Vec3 swirl = new Vec3(-to.z, 0, to.x).normalize().scale(0.06);
            if (d > 1.6) e.push(pull.x + swirl.x, 0.0, pull.z + swirl.z);
            e.hurtMarked = true;
            if (i % 10 == 0) {
                strike(e, 3.0F, magicSource(this));
                e.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 100, 0), this);
            }
        }
        if (i % 20 == 0) CeFx.sound(level, c, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.2F, 0.8F);
        if (i == 59) {
            CeFx.shockwave(level, c, ParticleTypes.SPLASH, 80, 0.6);
            CeFx.burst(level, c.add(0, 1, 0), ParticleTypes.BUBBLE, 30, 1.2, 0.1);
            CeFx.sound(level, c, SoundEvents.GENERIC_SPLASH, 1.5F, 0.6F);
            for (LivingEntity e : foesNear(c, 4.0)) {
                e.knockback(1.4, getX() - e.getX(), getZ() - e.getZ());
                e.push(0, 0.4, 0);
                e.hurtMarked = true;
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || tickCount % 20 != 0) return;
        // sempre encharcado: Raio o eletrocuta (fraqueza natural de um ser de agua)
        if (!hasEffect(CoreRegistries.SOAKED)) addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 60, 0, true, false));
        if (isInWaterRainOrBubble() && getHealth() < getMaxHealth()) heal(1.5F);
    }

    @Override
    protected void clientFx() {
        var r = random;
        if (r.nextInt(2) == 0) level().addParticle(ParticleTypes.FALLING_WATER, getRandomX(0.6), getY() + 0.8 + r.nextDouble() * 1.6, getRandomZ(0.6), 0, 0, 0);
        if (r.nextInt(3) == 0) level().addParticle(ParticleTypes.SPLASH, getRandomX(0.5), getY() + 0.2, getRandomZ(0.5), 0, 0.1, 0);
        if (isInWater() && r.nextInt(2) == 0) level().addParticle(ParticleTypes.BUBBLE, getRandomX(0.5), getY() + r.nextDouble() * 2.5, getRandomZ(0.5), 0, 0.05, 0);
    }

    @Override
    protected void deathFx(ServerLevel level) {
        Vec3 c = position().add(0, 1.2, 0);
        CeFx.burst(level, c, ParticleTypes.SPLASH, 80, 0.8, 0.3);
        CeFx.burst(level, c, ParticleTypes.BUBBLE_POP, 30, 0.6, 0.05);
        CeFx.burst(level, c, CeFx.dust(0x3FA9FF, 1.6F), 30, 0.7, 0.0);
        CeFx.sound(level, c, SoundEvents.GENERIC_SPLASH, 1.5F, 0.5F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.CONDUIT_AMBIENT_SHORT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.DROWNED_HURT_WATER;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ELDER_GUARDIAN_HURT;
    }
}
