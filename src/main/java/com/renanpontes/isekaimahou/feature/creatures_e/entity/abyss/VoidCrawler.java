package com.renanpontes.isekaimahou.feature.creatures_e.entity.abyss;

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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Rastejador do Vazio (Profundezas Abissais): artropode de sombra que escala paredes. Some e reaparece
 * atras da presa (piscar do vazio), salta sobre ela e morde corrompendo.
 */
public class VoidCrawler extends DimensionalCreature implements Enemy {
    private boolean pounceHit;

    public VoidCrawler(EntityType<? extends VoidCrawler> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(60, 6, 9, 0.34)
                .add(Attributes.ARMOR_TOUGHNESS, 1)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public Faction faction() {
        return Faction.ABYSS;
    }

    @Override
    protected int experience() {
        return 20;
    }

    @Override
    public boolean climbs() {
        return true;
    }

    @Override
    public double preferredRange() {
        return 0.2;
    }

    @Override
    public double chaseSpeed() {
        return 1.2;
    }

    @Override
    protected List<CreatureMove> createMoves() {
        return List.of(
                CreatureMove.of("blink").timing(6, 10).cooldown(140).range(5.0, 20).blind().perform(this::blink),
                CreatureMove.of("pounce").timing(6, 22).cooldown(60).range(1.2, 6.0).mobile()
                        .onStart(t -> pounceHit = false)
                        .perform(t -> {
                            Vec3 d = t.position().subtract(position()).multiply(1, 0, 1).normalize();
                            setDeltaMovement(d.x * 0.95, 0.5, d.z * 0.95);
                            hasImpulse = true;
                            playSound(SoundEvents.SPIDER_AMBIENT, 1.0F, 1.5F);
                        })
                        .during((t, i) -> {
                            if (i > 6 && !pounceHit && touching(t, 0.4)) {
                                pounceHit = true;
                                bite(t, 1.25F);
                            }
                        }),
                CreatureMove.of("attack").timing(5, 10).cooldown(20).range(0, 1.4).perform(t -> {
                    if (gapTo(t) <= 2.2) bite(t, 1.0F);
                }));
    }

    private void bite(LivingEntity t, float mult) {
        if (!(level() instanceof ServerLevel level)) return;
        if (meleeHit(t, mult, 0.3)) {
            t.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 80, 0), this);
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), this);
        }
        CeFx.burst(level, center(t), ParticleTypes.SQUID_INK, 8, 0.25, 0.02);
        CeFx.burst(level, center(t), CeFx.dust(0xA04BFF, 1.0F), 8, 0.3, 0.0);
        CeFx.sound(level, center(t), SoundEvents.SPIDER_HURT, 0.8F, 0.6F);
    }

    /** Piscar do vazio: some e reaparece atras do alvo. */
    private void blink(LivingEntity t) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 from = position();
        Vec3 behind = t.getLookAngle().multiply(1, 0, 1).normalize().scale(-2.2);
        Vec3 dest = t.position().add(behind);
        boolean ok = randomTeleport(dest.x, dest.y + 0.5, dest.z, false);
        if (!ok) ok = randomTeleport(t.getX() + (random.nextDouble() - 0.5) * 4, t.getY() + 0.5, t.getZ() + (random.nextDouble() - 0.5) * 4, false);
        if (!ok) return;
        CeFx.burst(level, from.add(0, 0.5, 0), ParticleTypes.PORTAL, 40, 0.5, 0.6);
        CeFx.burst(level, from.add(0, 0.5, 0), ParticleTypes.REVERSE_PORTAL, 20, 0.4, 0.05);
        CeFx.burst(level, position().add(0, 0.5, 0), ParticleTypes.PORTAL, 40, 0.5, 0.6);
        CeFx.sound(level, from, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.7F);
        CeFx.sound(level, position(), SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.7F);
        lookAt(t, 180.0F, 90.0F);
        putOnCooldown("attack", 0);
    }

    @Override
    protected void clientFx() {
        var r = random;
        if (r.nextInt(2) == 0) level().addParticle(ParticleTypes.PORTAL, getRandomX(0.8), getY() + r.nextDouble() * 0.8, getRandomZ(0.8),
                (r.nextDouble() - 0.5) * 0.5, -r.nextDouble() * 0.3, (r.nextDouble() - 0.5) * 0.5);
        if (r.nextInt(8) == 0) level().addParticle(ParticleTypes.ASH, getRandomX(0.8), getY() + 0.5, getRandomZ(0.8), 0, 0, 0);
    }

    @Override
    protected void deathFx(ServerLevel level) {
        Vec3 c = position().add(0, 0.4, 0);
        CeFx.burst(level, c, ParticleTypes.REVERSE_PORTAL, 40, 0.6, 0.05);
        CeFx.burst(level, c, ParticleTypes.SQUID_INK, 20, 0.5, 0.02);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SPIDER_STEP, 0.2F, 0.7F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6F;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return random.nextInt(3) == 0 ? SoundEvents.ENDERMITE_AMBIENT : SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }
}
