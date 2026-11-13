package com.renanpontes.isekaimahou.feature.ritual_warlock.entity;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PatronKind;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Senhora do Vazio: figura flutuante de vestido estrelado e coroa de orbes.
 * Voa em órbita ao redor do alvo. Especiais: Passo do Vazio (teleporta para as costas e golpeia) e
 * Abraço do Abismo (puxa todos para perto, escuridão e corrupção). Às vezes se teleporta ao ser ferida.
 */
public class VoidLadyEntity extends PatronEntity {
    private boolean embraceNext;

    public VoidLadyEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 130.0)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    public PatronKind kind() {
        return PatronKind.VOID_LADY;
    }

    @Override
    protected double baseHealth() {
        return 130.0;
    }

    @Override
    protected double baseDamage() {
        return 9.0;
    }

    @Override
    protected boolean flies() {
        return true;
    }

    @Override
    protected double meleeReach() {
        return 3.0;
    }

    /** Órbita em volta do alvo; mergulha quando o golpe está pronto. */
    @Override
    protected void moveInCombat(LivingEntity target, double dist) {
        Vec3 desired;
        if (attackCooldown <= 8) {
            desired = target.position().add(0, 0.8, 0);
        } else {
            double a = tickCount * 0.04 + getId();
            desired = target.position().add(Math.cos(a) * 5.0, 2.5, Math.sin(a) * 5.0);
        }
        steer(desired, enraged ? 0.045 : 0.032);
    }

    private void steer(Vec3 desired, double accel) {
        Vec3 to = desired.subtract(position());
        double len = to.length();
        if (len < 0.25) {
            setDeltaMovement(getDeltaMovement().scale(0.8));
            return;
        }
        setDeltaMovement(getDeltaMovement().scale(0.88).add(to.scale(1.0 / len).scale(accel * Math.min(len, 3.0))));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !isFree()) return;
        setNoGravity(true);
        if (getTarget() == null) {
            // Paira suavemente a ~2 blocos do chão.
            double ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
            double want = Math.max(ground + 2.0, getY());
            steer(new Vec3(getX(), Math.min(want, ground + 3.0), getZ()), 0.01);
        }
    }

    @Override
    protected void special(ServerLevel level, LivingEntity target) {
        if (embraceNext) abyssEmbrace(level);
        else voidStep(level, target);
        embraceNext = !embraceNext;
    }

    /** Passo do Vazio: some e reaparece nas costas do alvo, já golpeando. */
    private void voidStep(ServerLevel level, LivingEntity target) {
        Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(2.0));
        level.sendParticles(Vfx.reversePortal(), getX(), getY() + 1.5, getZ(), 40, 0.4, 1.0, 0.4, 0.05);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2F, 0.6F);
        teleportTo(behind.x, target.getY() + 0.5, behind.z);
        setDeltaMovement(Vec3.ZERO);
        faceTarget(target);
        level.sendParticles(Vfx.portal(), getX(), getY() + 1.5, getZ(), 60, 0.5, 1.0, 0.5, 0.3);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2F, 0.5F);
        triggerAnim("action", "attack");
        doHurtTarget(target);
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), this);
    }

    /** Abraço do Abismo: puxa quem estiver a até 10 blocos e envolve em trevas. */
    private void abyssEmbrace(ServerLevel level) {
        Vec3 c = position().add(0, 1.2, 0);
        for (double r = 6; r >= 1.5; r -= 1.5) ElementFx.ring(level, c, Element.SHADOW, r, (int) (8 * r));
        level.sendParticles(Vfx.ink(), c.x, c.y, c.z, 40, 3.0, 1.0, 3.0, 0.02);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.5F, 0.5F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10.0),
                e -> e != this && e.isAlive() && !(e instanceof PatronEntity))) {
            Vec3 d = c.subtract(e.position());
            double len = d.length();
            if (len > 10.0 || len < 1.0E-3) continue;
            Vec3 pull = d.scale(1.0 / len).scale(Math.min(1.6, 0.5 + len * 0.12));
            e.push(pull.x, 0.25, pull.z);
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 100, enraged ? 1 : 0), this);
            if (e instanceof Player) e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0), this);
        }
    }

    @Override
    protected void fallbackRanged(ServerLevel level, LivingEntity target) {
        ShulkerBullet bullet = new ShulkerBullet(level, this, target, Direction.Axis.Y);
        bullet.setPos(getX(), getY(0.7), getZ());
        level.addFreshEntity(bullet);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.HOSTILE, 1.2F, 0.6F);
    }

    @Override
    protected void onMeleeHit(LivingEntity target) {
        target.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 60, 0), this);
    }

    @Override
    protected void onEnrage(ServerLevel level) {
        blink(level);
        for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(12.0))) {
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0), this);
        }
    }

    /** Teleporte curto e aleatório (como um Enderman). */
    private void blink(ServerLevel level) {
        double ox = getX(), oy = getY(), oz = getZ();
        for (int i = 0; i < 12; i++) {
            double x = ox + (random.nextDouble() - 0.5) * 16.0;
            double y = oy + random.nextInt(5) - 1;
            double z = oz + (random.nextDouble() - 0.5) * 16.0;
            if (randomTeleport(x, y, z, true)) {
                level.sendParticles(Vfx.reversePortal(), ox, oy + 1.5, oz, 40, 0.4, 1.0, 0.4, 0.05);
                level.playSound(null, ox, oy, oz, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.7F);
                return;
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && isFree() && isAlive() && level() instanceof ServerLevel level && random.nextFloat() < 0.18F) blink(level);
        return hit;
    }

    @Override
    public boolean causeFallDamage(float distance, float mult, DamageSource source) {
        return false;
    }

    @Override
    protected void clientAmbient() {
        if (random.nextInt(2) == 0) {
            level().addParticle(Vfx.portal(), getRandomX(0.6), getY() + random.nextDouble() * getBbHeight(), getRandomZ(0.6),
                    (random.nextDouble() - 0.5) * 0.5, -random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.5);
        }
        if (random.nextInt(6) == 0) {
            level().addParticle(Vfx.reversePortal(), getRandomX(0.4), getY() + 0.2, getRandomZ(0.4), 0, 0.02, 0);
        }
    }

    @Override
    protected SoundEvent roarSound() {
        return SoundEvents.ENDERMAN_STARE;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }
}
