package com.renanpontes.isekaimahou.feature.hueco_mundo.entity;

import java.util.List;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * Fada Travessa: pequenina, de asas de vidro, brilha no escuro. Circula o alvo jogando faíscas que fazem brilhar e
 * prendem as pernas; quando apanha, some num piscar e reaparece em outro lugar, rindo.
 */
public class FeyPixie extends HmFlyer {
    private int shotCd = 30, blinkCd;

    public FeyPixie(EntityType<? extends FeyPixie> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 14).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6).add(Attributes.ATTACK_DAMAGE, 3).add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    public String modelId() {
        return "fey_pixie";
    }

    @Override
    public HmRealm side() {
        return HmRealm.FEY;
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("cast");
    }

    @Override
    protected double orbitHeight() {
        return 3.0;
    }

    @Override
    protected double orbitRadius() {
        return 5.0;
    }

    @Override
    protected double flySpeed() {
        return 1.2;
    }

    @Override
    protected void combatTick(LivingEntity t) {
        if (blinkCd > 0) blinkCd--;
        if (--shotCd <= 0 && hasLineOfSight(t)) {
            shotCd = 30 + random.nextInt(30);
            action("cast");
            HmBolt.shoot(this, getEyePosition(), t.getEyePosition(), HmBolt.SPARKLE, 3F, 0.9F);
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.8F);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && isAlive() && blinkCd == 0 && level() instanceof ServerLevel sl && getTarget() != null) {
            blinkCd = 60;
            sl.sendParticles(HmFx.dust(HmFx.FEY_PINK, 1.0F), getX(), getY() + 0.3, getZ(), 14, 0.3, 0.3, 0.3, 0);
            if (BossKit.blinkNear(this, getTarget(), 5)) {
                sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 8, 0.3, 0.3, 0.3, 0.02);
                playSound(SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 1.0F, 1.6F);
            }
        }
        return r;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide && tickCount % 3 == 0) {
            level().addParticle(HmFx.dust(HmFx.FEY_PINK, 0.6F), getX(), getY() + 0.3, getZ(), 0, -0.02, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.4F;
    }
}
