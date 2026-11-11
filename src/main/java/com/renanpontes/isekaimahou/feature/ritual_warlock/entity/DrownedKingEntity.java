package com.renanpontes.isekaimahou.feature.ritual_warlock.entity;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PatronKind;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Rei Afogado: rei inchado de pele verde-mar, coroa de coral e tridente.
 * Especiais: Maré Real (onda que empurra e encharca) e Legião Afogada (invoca afogados).
 * Arremessa tridentes de longe e nada rápido atrás do alvo dentro d'água (respira debaixo d'água pela tag
 * minecraft:can_breathe_under_water, no merge do módulo).
 */
public class DrownedKingEntity extends PatronEntity {
    private static final int MAX_MINIONS = 4;
    private boolean legionNext;

    public DrownedKingEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 180.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    public PatronKind kind() {
        return PatronKind.DROWNED_KING;
    }

    @Override
    protected double baseHealth() {
        return 180.0;
    }

    @Override
    protected double baseDamage() {
        return 12.0;
    }

    @Override
    protected void special(ServerLevel level, LivingEntity target) {
        if (legionNext && countMinions(level) < MAX_MINIONS) summonLegion(level, target);
        else royalTide(level);
        legionNext = !legionNext;
    }

    /** Maré Real: onda circular que arremessa, encharca e deixa lento. */
    private void royalTide(ServerLevel level) {
        Vec3 c = position();
        for (double r = 1.5; r <= 6.0; r += 1.5) ElementFx.ring(level, c.add(0, 0.3, 0), Element.WATER, r, (int) (10 * r));
        level.sendParticles(ParticleTypes.SPLASH, c.x, c.y + 0.5, c.z, 120, 3.0, 0.3, 3.0, 0.3);
        level.sendParticles(ParticleTypes.BUBBLE_POP, c.x, c.y + 0.8, c.z, 40, 2.5, 0.5, 2.5, 0.05);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundSource.HOSTILE, 1.5F, 0.6F);
        DamageSource src = damageSources().mobAttack(this);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(7.0),
                e -> e != this && e.isAlive() && !(e instanceof PatronEntity) && !(e instanceof Drowned))) {
            if (e.distanceToSqr(this) > 49.0) continue;
            Vec3 d = e.position().subtract(c);
            d = d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
            e.hurt(src, 4.0F + 0.6F * tier());
            e.push(d.x * 1.6, 0.45, d.z * 1.6);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 200, 0), this);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1), this);
        }
    }

    /** Legião Afogada: 2–3 afogados (alguns com tridente) surgem em volta do Rei. */
    private void summonLegion(ServerLevel level, LivingEntity target) {
        int n = enraged ? 3 : 2;
        for (int i = 0; i < n; i++) {
            Drowned d = EntityType.DROWNED.create(level);
            if (d == null) continue;
            double a = random.nextDouble() * Math.PI * 2;
            double x = getX() + Math.cos(a) * 2.5, z = getZ() + Math.sin(a) * 2.5;
            d.moveTo(x, getY(), z, random.nextFloat() * 360F, 0F);
            EventHooks.finalizeMobSpawn(d, level, level.getCurrentDifficultyAt(d.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            if (random.nextFloat() < 0.35F) {
                d.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
                d.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
            }
            d.setTarget(target);
            level.addFreshEntity(d);
            level.sendParticles(ParticleTypes.SPLASH, x, getY() + 0.5, z, 30, 0.4, 0.6, 0.4, 0.2);
            level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, getY() + 0.2, z, 15, 0.3, 0.2, 0.3, 0.1);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.DROWNED_AMBIENT_WATER, SoundSource.HOSTILE, 2.0F, 0.5F);
    }

    private int countMinions(ServerLevel level) {
        return level.getEntitiesOfClass(Drowned.class, getBoundingBox().inflate(16.0)).size();
    }

    @Override
    protected void fallbackRanged(ServerLevel level, LivingEntity target) {
        ThrownTrident trident = new ThrownTrident(level, this, new ItemStack(Items.TRIDENT));
        trident.pickup = AbstractArrow.Pickup.DISALLOWED;
        trident.setPos(getX(), getY(0.75), getZ());
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double dy = target.getY(0.4) - trident.getY();
        trident.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.15, dz, 1.7F, 4.0F);
        level.addFreshEntity(trident);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.HOSTILE, 1.2F, 0.7F);
    }

    @Override
    protected void onMeleeHit(LivingEntity target) {
        target.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 100, 0), this);
    }

    @Override
    protected void onEnrage(ServerLevel level) {
        royalTide(level);
        LivingEntity t = getTarget();
        if (t != null && countMinions(level) < MAX_MINIONS) summonLegion(level, t);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // Dentro d'água o Rei "nada" direto até o alvo.
        LivingEntity t = getTarget();
        if (!level().isClientSide && isFree() && isInWater() && t != null) {
            Vec3 to = t.position().subtract(position());
            if (to.lengthSqr() > 4.0) {
                setDeltaMovement(getDeltaMovement().scale(0.9).add(to.normalize().scale(0.06)));
            }
        }
    }

    @Override
    protected void clientAmbient() {
        if (random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.DRIPPING_WATER, getRandomX(0.6), getY() + getBbHeight() * (0.4 + random.nextDouble() * 0.5),
                    getRandomZ(0.6), 0, 0, 0);
        }
        if (isInWater() && random.nextInt(2) == 0) {
            level().addParticle(ParticleTypes.BUBBLE, getRandomX(0.6), getY() + random.nextDouble() * getBbHeight(), getRandomZ(0.6), 0, 0.05, 0);
        }
        if (random.nextInt(12) == 0) {
            level().addParticle(ParticleTypes.FALLING_WATER, getRandomX(0.3), getY() + getBbHeight() * 0.75, getRandomZ(0.3), 0, 0, 0);
        }
    }

    @Override
    protected SoundEvent roarSound() {
        return SoundEvents.DROWNED_AMBIENT;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return isInWater() ? SoundEvents.DROWNED_AMBIENT_WATER : SoundEvents.DROWNED_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.DROWNED_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.DROWNED_DEATH;
    }
}
