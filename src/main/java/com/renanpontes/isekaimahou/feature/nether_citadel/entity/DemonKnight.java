package com.renanpontes.isekaimahou.feature.nether_citadel.entity;

import java.util.List;

import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Cavaleiro Infernal: armadura negra com chifres, escudo e espada em brasa.
 * <ul>
 *   <li>Escudo: projéteis vindos da frente causam só 40% (dê a volta ou use magia de área);</li>
 *   <li><b>Investida Infernal</b>: marca uma linha até o alvo (6–14 blocos) e avança por ela, ferindo e queimando;</li>
 *   <li>golpes corpo a corpo deixam o alvo em chamas.</li>
 * </ul>
 */
public class DemonKnight extends CitadelDemon {
    private static final int CHARGE_COLOR = 0xFF5A1E;
    private int chargeCd = 60;
    private int chargeTimer;

    public DemonKnight(EntityType<? extends DemonKnight> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0)
                .add(Attributes.ATTACK_DAMAGE, 11.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected String modelId() {
        return "demon_knight";
    }

    @Override
    protected List<String> actions() {
        return List.of("attack", "charge", "block");
    }

    @Override
    protected void registerCombatGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        action("attack");
        boolean hit = super.doHurtTarget(target);
        if (hit) target.igniteForSeconds(3.0F);
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof Projectile proj && chargeTimer <= 0) {
            Vec3 toShot = proj.position().subtract(position()).multiply(1, 0, 1);
            Vec3 facing = Vec3.directionFromRotation(0, yBodyRot);
            if (toShot.lengthSqr() > 1.0E-4 && toShot.normalize().dot(facing) > 0.3) {
                amount *= 0.4F;
                action("block");
                playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F);
            }
        }
        return super.hurt(source, amount);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        if (chargeCd > 0) chargeCd--;
        if (chargeTimer > 0) {
            chargeTimer--;
            getNavigation().stop();
            return;
        }
        LivingEntity target = getTarget();
        if (target == null || chargeCd > 0 || !onGround()) return;
        double d = distanceTo(target);
        if (d >= 6 && d <= 14 && hasLineOfSight(target)) startCharge(sl, target);
    }

    private void startCharge(ServerLevel sl, LivingEntity target) {
        chargeCd = 140 + random.nextInt(60);
        chargeTimer = 22;
        Vec3 from = position();
        Vec3 dir = target.position().subtract(from).multiply(1, 0, 1).normalize();
        Vec3 to = from.add(dir.scale(Math.min(14.0, distanceTo(target) + 2.0)));
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * (180.0 / Math.PI)) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
        action("charge");
        playSound(SoundEvents.RAVAGER_ROAR, 0.8F, 1.4F);
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.2F;
        Telegraph.delayed(sl, Telegraph.Shape.line(from, to, 1.6F).warmup(16).color(CHARGE_COLOR).owner(this).noRunes(), hits -> {
            if (!isAlive()) return;
            for (LivingEntity e : hits) {
                if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) {
                    e.igniteForSeconds(4.0F);
                    e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.8, 0.35, dir.z * 0.8));
                    e.hurtMarked = true;
                }
            }
            Vec3 end = to;
            for (int i = 0; i <= 10; i++) {
                Vec3 p = from.add(end.subtract(from).scale(i / 10.0));
                sl.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.6, p.z, 3, 0.2, 0.3, 0.2, 0.02);
            }
            if (sl.noCollision(this, getBoundingBox().move(end.subtract(position())))) teleportTo(end.x, end.y, end.z);
            playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.7F);
        });
    }
}
