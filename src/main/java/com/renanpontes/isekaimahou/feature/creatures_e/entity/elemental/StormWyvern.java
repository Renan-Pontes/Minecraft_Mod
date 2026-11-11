package com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.creatures_e.CeFx;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.CreatureMove;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalCreature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Wyvern de Tempestade (Ceus de Tempestade): drake voador. Morde, investe e ruge chamando raios
 * em pontos marcados no chao (da para desviar vendo as faiscas). Imune a raios (eles o curam) e
 * mais forte durante tempestades.
 */
public class StormWyvern extends DimensionalCreature implements Enemy {
    private final List<Vec3> strikes = new ArrayList<>();
    private boolean diveHit;

    public StormWyvern(EntityType<? extends StormWyvern> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(120, 10, 13, 0.3)
                .add(Attributes.FLYING_SPEED, 0.18)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.ARMOR_TOUGHNESS, 2)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    public Faction faction() {
        return Faction.ELEMENTAL;
    }

    @Override
    protected int experience() {
        return 35;
    }

    @Override
    public boolean flies() {
        return true;
    }

    @Override
    public double preferredRange() {
        return 2.5;
    }

    @Override
    public double hoverHeight() {
        return 2.5;
    }

    @Override
    public double chaseSpeed() {
        return 1.25;
    }

    @Override
    protected List<CreatureMove> createMoves() {
        return List.of(
                CreatureMove.of("roar").timing(16, 30).cooldown(100).range(0, 24)
                        .onStart(this::markStrikes)
                        .during(this::storm),
                CreatureMove.of("dive").timing(10, 34).cooldown(110).range(4.5, 18).mobile()
                        .onStart(t -> {
                            diveHit = false;
                            CeFx.sound(level(), position(), SoundEvents.PHANTOM_SWOOP, 1.5F, 0.6F);
                        })
                        .during(this::diveTick),
                CreatureMove.of("attack").timing(7, 14).cooldown(24).range(0, 2.4).perform(this::bite));
    }

    private void bite(LivingEntity t) {
        if (!(level() instanceof ServerLevel level) || gapTo(t) > 3.2) return;
        meleeHit(t, 1.0F, 0.8);
        CeFx.burst(level, center(t), ParticleTypes.ELECTRIC_SPARK, 14, 0.3, 0.1);
        CeFx.sound(level, center(t), SoundEvents.PHANTOM_BITE, 1.2F, 0.6F);
    }

    /** Marca os pontos onde os raios vao cair (o alvo e alguns em volta). */
    private void markStrikes(LivingEntity t) {
        strikes.clear();
        int n = level().isThundering() ? 5 : 3;
        strikes.add(t.position());
        for (int i = 1; i < n; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = 2.0 + random.nextDouble() * 3.0;
            double x = t.getX() + Math.cos(a) * d;
            double z = t.getZ() + Math.sin(a) * d;
            int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            double yy = Math.abs(y - t.getY()) < 6 ? y : t.getY();
            strikes.add(new Vec3(x, yy, z));
        }
        CeFx.sound(level(), position(), SoundEvents.ENDER_DRAGON_GROWL, 1.6F, 1.4F);
    }

    private void storm(LivingEntity t, int i) {
        if (!(level() instanceof ServerLevel level)) return;
        if (i < 16) {
            if (i % 3 == 0) {
                for (Vec3 p : strikes) CeFx.ring(level, p.add(0, 0.15, 0), 1.6, ParticleTypes.ELECTRIC_SPARK, 14, 0.05);
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 3, 0.8, 0.5, 0.8, 0.1);
            return;
        }
        int idx = (i - 16) / 3;
        if ((i - 16) % 3 == 0 && idx < strikes.size()) bolt(level, strikes.get(idx));
    }

    /** Raio visual + dano proprio (sem fogo e sem ferir outros elementais). Encharcados sao eletrocutados. */
    private void bolt(ServerLevel level, Vec3 p) {
        LightningBolt lb = EntityType.LIGHTNING_BOLT.create(level);
        if (lb != null) {
            lb.moveTo(p.x, p.y, p.z);
            lb.setVisualOnly(true);
            level.addFreshEntity(lb);
        }
        CeFx.burst(level, p.add(0, 0.5, 0), ParticleTypes.ELECTRIC_SPARK, 30, 0.6, 0.3);
        CeFx.sound(level, p, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.0F, 1.0F);
        for (LivingEntity e : foesNear(p.add(0, 1.0, 0), 2.2)) {
            float dmg = 9.0F;
            if (e.hasEffect(CoreRegistries.SOAKED)) {
                dmg *= 2.0F;
                e.removeEffect(CoreRegistries.SOAKED);
                e.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, 40, 0), this);
            }
            strike(e, dmg, magicSource(this));
        }
    }

    private void diveTick(LivingEntity t, int i) {
        if (!(level() instanceof ServerLevel level) || i < 10) return;
        if (!diveHit && i < 30) dashAt(t, 0.85, 0.0);
        if (i % 2 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.8, getZ(), 4, 0.8, 0.3, 0.8, 0.05);
        if (!diveHit && touching(t, 0.5)) {
            diveHit = true;
            meleeHit(t, 1.2F, 1.6);
            t.push(0, 0.4, 0);
            CeFx.burst(level, center(t), ParticleTypes.ELECTRIC_SPARK, 25, 0.5, 0.2);
            CeFx.sound(level, center(t), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.6F);
            setDeltaMovement(getDeltaMovement().scale(-0.3).add(0, 0.6, 0));
            endMove();
        }
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypeTags.IS_LIGHTNING) || super.isInvulnerableTo(source);
    }

    /** Raios naturais recarregam a wyvern em vez de feri-la. */
    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        heal(10.0F);
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (tickCount % 16 == 0 && getDeltaMovement().lengthSqr() > 0.01) playSound(SoundEvents.ENDER_DRAGON_FLAP, 0.7F, 1.3F);
        if (tickCount % 40 == 0 && level().isThundering() && level().canSeeSky(BlockPos.containing(getX(), getEyeY(), getZ()))) {
            addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, true, true));
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, true));
        }
    }

    @Override
    protected void clientFx() {
        var r = random;
        if (r.nextInt(3) == 0) level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(1.2), getY() + 0.6 + r.nextDouble() * 0.8, getRandomZ(1.2), 0, 0, 0);
        if ("roar".equals(actionName()) && actionAge() < 16) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.3, getZ(), (r.nextDouble() - 0.5) * 0.5, 0.2, (r.nextDouble() - 0.5) * 0.5);
        }
    }

    @Override
    protected void deathFx(ServerLevel level) {
        Vec3 c = position().add(0, 0.8, 0);
        CeFx.burst(level, c, ParticleTypes.ELECTRIC_SPARK, 50, 1.0, 0.3);
        CeFx.burst(level, c, ParticleTypes.CLOUD, 20, 0.8, 0.05);
        CeFx.sound(level, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.6F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.55F;
    }

    @Override
    protected float getSoundVolume() {
        return 1.6F;
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
        return SoundEvents.PHANTOM_DEATH;
    }
}
