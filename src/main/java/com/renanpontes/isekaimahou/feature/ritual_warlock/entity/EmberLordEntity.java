package com.renanpontes.isekaimahou.feature.ritual_warlock.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PatronKind;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Lorde das Brasas: demônio de obsidiana com rachaduras de lava e uma lâmina derretida.
 * Especiais: Erupção (anel de fogo ao redor) e Chuva de Brasas (bolas de fogo caindo sobre o alvo).
 * Enfurecido: aura que queima quem chega perto e rastro de fogo.
 */
public class EmberLordEntity extends PatronEntity {
    private boolean rainNext;

    public EmberLordEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 160.0)
                .add(Attributes.ATTACK_DAMAGE, 11.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    public PatronKind kind() {
        return PatronKind.EMBER_LORD;
    }

    @Override
    protected double baseHealth() {
        return 160.0;
    }

    @Override
    protected double baseDamage() {
        return 11.0;
    }

    @Override
    protected void special(ServerLevel level, LivingEntity target) {
        if (rainNext) emberRain(level, target);
        else eruption(level);
        rainNext = !rainNext;
    }

    /** Erupção: três anéis de fogo saem do Lorde; queima e arremessa quem estiver a até 5,5 blocos. */
    private void eruption(ServerLevel level) {
        Vec3 c = position();
        for (double r = 1.5; r <= 4.5; r += 1.5) ElementFx.ring(level, c.add(0, 0.2, 0), Element.FIRE, r, (int) (12 * r));
        level.sendParticles(Vfx.flame(), c.x, c.y + 0.3, c.z, 80, 2.5, 0.2, 2.5, 0.08);
        level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.5, c.z, 20, 2.0, 0.3, 2.0, 0.0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.2F, 0.7F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5F, 0.5F);
        DamageSource src = damageSources().mobAttack(this);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5.5),
                e -> e != this && e.isAlive() && !(e instanceof PatronEntity))) {
            if (e.distanceToSqr(this) > 5.5 * 5.5) continue;
            e.hurt(src, 6.0F + 0.8F * tier());
            e.igniteForSeconds(5.0F);
            Vec3 d = e.position().subtract(c);
            d = d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
            e.push(d.x * 0.9, 0.6, d.z * 0.9);
            e.hurtMarked = true;
        }
    }

    /** Chuva de Brasas: bolas de fogo pequenas caem do céu em volta do alvo. */
    private void emberRain(ServerLevel level, LivingEntity target) {
        int n = 6 + tier() / 2 + (enraged ? 3 : 0);
        for (int i = 0; i < n; i++) {
            double x = target.getX() + (random.nextDouble() - 0.5) * 8.0;
            double z = target.getZ() + (random.nextDouble() - 0.5) * 8.0;
            double y = target.getY() + 10.0 + random.nextDouble() * 4.0;
            SmallFireball fb = new SmallFireball(level, x, y, z, new Vec3((random.nextDouble() - 0.5) * 0.1, -1.0, (random.nextDouble() - 0.5) * 0.1));
            fb.setOwner(this);
            level.addFreshEntity(fb);
            level.sendParticles(Vfx.flame(), x, target.getY() + 0.1, z, 4, 0.3, 0.0, 0.3, 0.01);
        }
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.5F, 0.6F);
    }

    @Override
    protected void fallbackRanged(ServerLevel level, LivingEntity target) {
        for (int i = 0; i < 3; i++) {
            double dx = target.getX() - getX() + random.nextGaussian() * 0.6;
            double dy = target.getY(0.5) - getY(0.75);
            double dz = target.getZ() - getZ() + random.nextGaussian() * 0.6;
            SmallFireball fb = new SmallFireball(level, this, new Vec3(dx, dy, dz).normalize());
            fb.setPos(getX(), getY(0.75), getZ());
            level.addFreshEntity(fb);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.2F, 0.7F);
    }

    @Override
    protected void onMeleeHit(LivingEntity target) {
        target.igniteForSeconds(4.0F);
    }

    @Override
    protected void onEnrage(ServerLevel level) {
        eruption(level);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel level) || !isFree() || !enraged) return;
        // Aura ardente e rastro de fogo quando enfurecido.
        if (tickCount % 20 == 0) {
            ElementFx.ring(level, position().add(0, 0.3, 0), Element.FIRE, 2.5, 20);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.5),
                    e -> e != this && e.isAlive() && !(e instanceof PatronEntity))) {
                e.igniteForSeconds(3.0F);
            }
        }
        if (tickCount % 10 == 0 && getDeltaMovement().horizontalDistanceSqr() > 0.001 && EventHooks.canEntityGrief(level, this)) {
            BlockPos feet = blockPosition();
            if (level.getBlockState(feet).isAir() && BaseFireBlock.canBePlacedAt(level, feet, Direction.UP)) {
                level.setBlockAndUpdate(feet, BaseFireBlock.getState(level, feet));
            }
        }
    }

    @Override
    protected void clientAmbient() {
        if (random.nextInt(2) == 0) {
            level().addParticle(Vfx.flame(), getRandomX(0.6), getY() + random.nextDouble() * getBbHeight(), getRandomZ(0.6),
                    0, 0.02, 0);
        }
        if (random.nextInt(5) == 0) {
            level().addParticle(Vfx.largeSmoke(), getRandomX(0.5), getY() + getBbHeight() * 0.9, getRandomZ(0.5), 0, 0.03, 0);
        }
        if (random.nextInt(25) == 0) {
            level().addParticle(ParticleTypes.LAVA, getRandomX(0.6), getY() + getBbHeight() * 0.6, getRandomZ(0.6), 0, 0, 0);
        }
    }

    @Override
    protected SoundEvent roarSound() {
        return SoundEvents.RAVAGER_ROAR;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isFree() ? SoundEvents.BLAZE_AMBIENT : SoundEvents.BLAZE_BURN;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BLAZE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLAZE_DEATH;
    }
}
