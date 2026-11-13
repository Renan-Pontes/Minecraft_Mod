package com.renanpontes.isekaimahou.feature.world_events.entity;

import java.util.List;

import com.renanpontes.isekaimahou.core.boss.Telegraph;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Atirador Estelar: fica de longe e dispara um raio de plasma em linha reta. O raio é AVISADO no chão antes
 * (faixa verde enchendo) — dê um passo para o lado. De perto ele só sabe dar cabeçada fraca.
 */
public class AlienSpitter extends AlienMob {
    private static final int BEAM_COLOR = 0x7CFF6A;
    private static final float BEAM_RANGE = 18F;
    private int beamCd = 60;
    private int aiming;

    public AlienSpitter(EntityType<? extends AlienSpitter> type, Level level) {
        super(type, level);
        this.xpReward = 9;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 11.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 36.0);
    }

    @Override
    protected String modelId() {
        return "world_events_alien_spitter";
    }

    @Override
    protected List<String> actions() {
        return List.of("attack", "shoot");
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        action("attack");
        return super.doHurtTarget(target);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        LivingEntity target = getTarget();
        if (aiming > 0) {
            // Parado enquanto carrega: é a hora de o jogador sair da faixa
            getNavigation().stop();
            aiming--;
            return;
        }
        if (beamCd > 0) beamCd--;
        if (target == null || !target.isAlive() || beamCd > 0) return;
        double d2 = distanceToSqr(target);
        if (d2 < 9 || d2 > BEAM_RANGE * BEAM_RANGE || !hasLineOfSight(target)) return;

        Vec3 from = position();
        Vec3 flat = new Vec3(target.getX() - from.x, 0, target.getZ() - from.z);
        if (flat.lengthSqr() < 0.01) return;
        Vec3 dir = flat.normalize();
        Vec3 to = from.add(dir.scale(BEAM_RANGE));
        getLookControl().setLookAt(target, 60F, 60F);
        aiming = 20;
        beamCd = 80 + random.nextInt(40);
        action("shoot");
        playSound(SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.8F);
        Telegraph.delayed(sl, Telegraph.Shape.line(from, to, 1.1F).warmup(18).color(BEAM_COLOR).owner(this),
                hits -> fire(sl, from, dir, hits));
    }

    private void fire(ServerLevel sl, Vec3 from, Vec3 dir, List<LivingEntity> hits) {
        if (!isAlive()) return;
        for (double d = 0.5; d < BEAM_RANGE; d += 0.6) {
            Vec3 p = from.add(dir.scale(d));
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 1.0, p.z, 2, 0.1, 0.1, 0.1, 0.02);
        }
        playSound(SoundEvents.GUARDIAN_ATTACK, 0.8F, 1.6F);
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.6F;
        for (LivingEntity e : hits) {
            if (e instanceof AlienMob) continue;
            e.hurt(damageSources().indirectMagic(this, this), dmg);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.4F + random.nextFloat() * 0.2F;
    }
}
