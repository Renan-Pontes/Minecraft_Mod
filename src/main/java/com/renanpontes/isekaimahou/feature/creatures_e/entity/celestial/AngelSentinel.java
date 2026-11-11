package com.renanpontes.isekaimahou.feature.creatures_e.entity.celestial;

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
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Anjo Sentinela (Cidade de Cristal): guarda alada com lanca de luz. Estoca (deixa Exposto), arremessa
 * dardos de luz, marca o chao com o Julgamento (coluna de luz que da para evitar) e ergue a Egide quando ferido.
 * Caca invasores e mortos-vivos; chama os outros guardas quando atacado.
 */
public class AngelSentinel extends DimensionalCreature implements Enemy {
    private Vec3 judgmentMark;

    public AngelSentinel(EntityType<? extends AngelSentinel> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(110, 14, 13, 0.3)
                .add(Attributes.ARMOR_TOUGHNESS, 4)
                .add(Attributes.FLYING_SPEED, 0.15)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    @Override
    public Faction faction() {
        return Faction.CELESTIAL;
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
        return 1.6;
    }

    @Override
    public double hoverHeight() {
        return 0.8;
    }

    @Override
    protected void registerTargetGoals() {
        super.registerTargetGoals();
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                e -> e.getType().is(EntityTypeTags.UNDEAD)));
    }

    @Override
    protected List<CreatureMove> createMoves() {
        return List.of(
                CreatureMove.of("aegis").timing(10, 20).cooldown(600).range(0, 40).blind()
                        .when(t -> getHealth() < getMaxHealth() * 0.4F)
                        .perform(t -> aegis()),
                CreatureMove.of("judgment").timing(28, 40).cooldown(220).range(0, 20)
                        .onStart(t -> {
                            judgmentMark = t.position();
                            CeFx.sound(level(), position(), SoundEvents.BEACON_ACTIVATE, 1.4F, 1.5F);
                        })
                        .during(this::judgment),
                CreatureMove.of("attack").timing(8, 14).cooldown(24).range(0, 3.0).perform(this::thrust),
                CreatureMove.of("cast").timing(12, 20).cooldown(50).range(4.0, 24).perform(this::javelin));
    }

    /** Estocada com a lanca (alcance maior que um golpe normal). */
    private void thrust(LivingEntity t) {
        if (!(level() instanceof ServerLevel level) || gapTo(t) > 3.8) return;
        if (meleeHit(t, 1.0F, 0.7)) t.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 60, 0), this);
        Vec3 from = position().add(0, 1.4, 0);
        CeFx.line(level, from, center(t), ParticleTypes.END_ROD, 3);
        CeFx.burst(level, center(t), ParticleTypes.ENCHANTED_HIT, 12, 0.3, 0.1);
        CeFx.sound(level, center(t), SoundEvents.TRIDENT_HIT, 1.0F, 1.3F);
    }

    private void javelin(LivingEntity t) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 from = position().add(0, 2.0, 0).add(getLookAngle().scale(0.5));
        DimensionalBolt.fire(this, BoltKind.LIGHT_SPEAR, from, center(t), 1.8F, 11.0F, 1.0F);
        CeFx.burst(level, from, ParticleTypes.END_ROD, 8, 0.2, 0.05);
        CeFx.sound(level, from, SoundEvents.TRIDENT_THROW.value(), 1.0F, 1.4F);
    }

    /** Julgamento: circulo de luz no ponto marcado que vira uma coluna sagrada. */
    private void judgment(LivingEntity t, int i) {
        if (!(level() instanceof ServerLevel level) || judgmentMark == null) return;
        Vec3 m = judgmentMark.add(0, 0.1, 0);
        if (i < 28) {
            if (i % 3 == 0) {
                double r = 2.8 - i * 0.04;
                CeFx.ring(level, m, r, CeFx.dust(0xFFE27A, 1.2F), 24, 0.0);
                CeFx.ring(level, m, r * 0.5, ParticleTypes.END_ROD, 8, 0.02);
            }
            return;
        }
        if (i == 28) {
            for (int h = 0; h < 14; h++) {
                level.sendParticles(ParticleTypes.END_ROD, m.x, m.y + h * 0.7, m.z, 4, 0.35, 0.2, 0.35, 0.02);
            }
            CeFx.burst(level, m.add(0, 0.5, 0), ParticleTypes.FLASH, 1, 0.0, 0.0);
            CeFx.shockwave(level, m.add(0, 0.2, 0), ParticleTypes.END_ROD, 40, 0.3);
            CeFx.sound(level, m, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4F, 1.7F);
            CeFx.sound(level, m, SoundEvents.AMETHYST_BLOCK_BREAK, 1.5F, 0.6F);
            for (LivingEntity e : foesNear(m.add(0, 1.0, 0), 2.8)) {
                strike(e, 14.0F, magicSource(this));
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), this);
                e.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 80, 0), this);
            }
        } else if (i % 2 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, m.x, m.y + 1.5, m.z, 3, 0.3, 1.5, 0.3, 0.01);
        }
    }

    /** Egide: absorcao e resistencia por alguns segundos. */
    private void aegis() {
        if (!(level() instanceof ServerLevel level)) return;
        addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 300, 2));
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 1));
        for (int h = 0; h < 4; h++) CeFx.ring(level, position().add(0, 0.3 + h * 0.6, 0), 1.0, CeFx.dust(0xFFE27A, 1.0F), 20, 0.0);
        CeFx.burst(level, position().add(0, 1.2, 0), ParticleTypes.TOTEM_OF_UNDYING, 20, 0.5, 0.2);
        CeFx.sound(level, position(), SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.4F);
    }

    @Override
    protected void onHurtBy(LivingEntity attacker, DamageSource source, float amount) {
        CelestialHost.alert(this, attacker);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && tickCount % 20 == 0 && getDeltaMovement().lengthSqr() > 0.01) playSound(SoundEvents.ENDER_DRAGON_FLAP, 0.35F, 1.7F);
    }

    @Override
    protected void clientFx() {
        var r = random;
        if (r.nextInt(4) == 0) level().addParticle(ParticleTypes.END_ROD, getRandomX(0.9), getY() + 1.2 + r.nextDouble() * 1.2, getRandomZ(0.9), 0, -0.02, 0);
        if (r.nextInt(3) == 0) level().addParticle(CeFx.dust(0xFFE27A, 0.6F), getX() + (r.nextDouble() - 0.5) * 0.5, getY() + 2.55, getZ() + (r.nextDouble() - 0.5) * 0.5, 0, 0, 0);
    }

    @Override
    protected void deathFx(ServerLevel level) {
        Vec3 c = position().add(0, 1.2, 0);
        CeFx.burst(level, c, ParticleTypes.END_ROD, 40, 0.5, 0.1);
        CeFx.burst(level, c, CeFx.dust(0xFFFFFF, 1.5F), 30, 0.6, 0.0);
        for (int h = 0; h < 8; h++) level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + h * 0.8, getZ(), 2, 0.1, 0.2, 0.1, 0.01);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_RESONATE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_DAMAGE;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BEACON_DEACTIVATE;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.3F;
    }
}
