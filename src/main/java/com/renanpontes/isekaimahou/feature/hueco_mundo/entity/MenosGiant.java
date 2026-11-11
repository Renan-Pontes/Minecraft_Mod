package com.renanpontes.isekaimahou.feature.hueco_mundo.entity;

import java.util.List;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Menos Gigante: colosso de manto negro com máscara branca de nariz comprido (homenagem aos Menos Grande). Lento e
 * enorme: pisões em anel quando cercado e o CERO — carrega uma esfera vermelha na ponta da máscara e dispara um feixe
 * em linha reta (a faixa no chão mostra onde; dá para sair). Raro nas florestas de quartzo.
 */
public class MenosGiant extends HmMob {
    private static final int CERO_WARMUP = 36;
    private int ceroCd = 80, stompCd = 60, ceroAt = -1, stompAt = -1;
    private Vec3 ceroFrom = Vec3.ZERO, ceroTo = Vec3.ZERO;

    public MenosGiant(EntityType<? extends MenosGiant> type, Level level) {
        super(type, level);
        this.xpReward = 60;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 200).add(Attributes.ARMOR, 10)
                .add(Attributes.MOVEMENT_SPEED, 0.17).add(Attributes.ATTACK_DAMAGE, 14).add(Attributes.FOLLOW_RANGE, 48)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.STEP_HEIGHT, 2.0);
    }

    @Override
    public String modelId() {
        return "menos_giant";
    }

    @Override
    public HmRealm side() {
        return HmRealm.HOLLOW;
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("cero", "stomp");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 32F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || ceroAt >= 0 || stompAt >= 0;
    }

    /** Ponta da máscara (de onde sai o Cero). */
    private Vec3 nose() {
        Vec3 f = Vec3.directionFromRotation(0, getYRot());
        return position().add(f.x * 1.6, 7.4, f.z * 1.6);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel sl = (ServerLevel) level();
        if (ceroAt >= 0) {
            float yaw = (float) Math.toDegrees(Math.atan2(ceroTo.z - getZ(), ceroTo.x - getX())) - 90F;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
            Vec3 n = nose();
            float charge = 1F - ceroAt / (float) CERO_WARMUP;
            sl.sendParticles(HmFx.dust(HmFx.CERO_RED, 1.0F + charge * 2.5F), n.x, n.y, n.z, 3, 0.15 + charge * 0.3, 0.15 + charge * 0.3, 0.15 + charge * 0.3, 0);
            if (--ceroAt < 0) fireCero(sl);
            return;
        }
        if (stompAt >= 0) {
            if (--stompAt < 0) stomp(sl);
            return;
        }
        if (ceroCd > 0) ceroCd--;
        if (stompCd > 0) stompCd--;
        LivingEntity t = liveTarget();
        if (t == null) return;
        double d = distanceTo(t);
        if (stompCd == 0 && d < 6.5) {
            stompCd = 100;
            stompAt = 18;
            action("stomp");
            Telegraph.show(sl, Telegraph.Shape.ring(position(), 1.5F, 7F).warmup(18).color(Telegraph.WARNING).owner(this).vertical(0.5, 1.2));
            return;
        }
        if (ceroCd == 0 && d < 34 && hasLineOfSight(t)) {
            ceroCd = 180 + random.nextInt(60);
            ceroAt = CERO_WARMUP;
            Vec3 dir = t.position().subtract(position()).multiply(1, 0, 1).normalize();
            ceroFrom = position().add(dir.scale(1.5));
            ceroTo = ceroFrom.add(dir.scale(30));
            Telegraph.show(sl, Telegraph.Shape.line(ceroFrom, ceroTo, 3.2F).warmup(CERO_WARMUP).color(HmFx.CERO_RED).owner(this));
            action("cero");
            playSound(SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.5F);
            return;
        }
        if (d > 3 && (tickCount % 10 == 0 || getNavigation().isDone())) getNavigation().moveTo(t, 1.0);
        getLookControl().setLookAt(t, 20F, 20F);
    }

    private void fireCero(ServerLevel sl) {
        if (!isAlive()) return;
        Telegraph.Shape line = Telegraph.Shape.line(ceroFrom, ceroTo, 3.2F);
        for (LivingEntity e : Telegraph.entitiesIn(sl, line)) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().indirectMagic(this, this), 18F)) e.igniteForSeconds(3F);
        }
        HmFx.beam(sl, nose(), ceroTo.add(0, 1, 0), HmFx.CERO_RED, 1.4F);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, ceroTo.x, ceroTo.y + 1, ceroTo.z, 1, 0, 0, 0, 0);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.6F);
        BossKit.shake(sl, ceroTo, 24, 1.2F, 16);
    }

    private void stomp(ServerLevel sl) {
        if (!isAlive()) return;
        Telegraph.Shape ring = Telegraph.Shape.ring(position(), 1.5F, 7F).vertical(0.5, 1.2);
        for (LivingEntity e : Telegraph.entitiesIn(sl, ring)) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().mobAttack(this), 12F)) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.7, 0));
                e.hurtMarked = true;
            }
        }
        HmFx.ring(sl, position().add(0, 0.2, 0), 5.0, 40, ParticleTypes.CLOUD, 0.08);
        playSound(SoundEvents.RAVAGER_STEP, 3.0F, 0.5F);
        BossKit.shake(sl, position(), 20, 1.0F, 12);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected float getSoundVolume() {
        return 2.5F;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.WARDEN_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.WARDEN_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }
}
