package com.renanpontes.isekaimahou.feature.hueco_mundo.entity;

import java.util.List;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Vazio Brutamontes: gorila de máscara com chifres e braços enormes. Soco forte e, a cada ~6 s, ESMAGA o chão (círculo
 * marcado no chão, dá para sair) arremessando quem ficou dentro.
 */
public class HollowBrute extends HmMob {
    private int slamCd = 60;
    private int slamAt = -1;

    public HollowBrute(EntityType<? extends HollowBrute> type, Level level) {
        super(type, level);
        this.xpReward = 15;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 70).add(Attributes.ARMOR, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.24).add(Attributes.ATTACK_DAMAGE, 11).add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6).add(Attributes.STEP_HEIGHT, 1.25);
    }

    @Override
    public String modelId() {
        return "hollow_brute";
    }

    @Override
    public HmRealm side() {
        return HmRealm.HOLLOW;
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("punch", "slam");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || slamAt >= 0;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel sl = (ServerLevel) level();
        if (slamAt >= 0) {
            if (--slamAt < 0) slamImpact(sl);
            return;
        }
        if (slamCd > 0) slamCd--;
        LivingEntity t = liveTarget();
        if (t != null && slamCd == 0 && onGround() && distanceTo(t) < 6.5) {
            slamCd = 120 + random.nextInt(40);
            slamAt = 16;
            action("slam");
            getNavigation().stop();
            Telegraph.show(sl, Telegraph.Shape.circle(position(), 3.8F).warmup(16).color(Telegraph.WARNING).owner(this).noRunes());
            playSound(SoundEvents.RAVAGER_ROAR, 0.8F, 1.3F);
        }
    }

    private void slamImpact(ServerLevel sl) {
        if (!isAlive()) return;
        Telegraph.Shape zone = Telegraph.Shape.circle(position(), 3.8F);
        for (LivingEntity e : Telegraph.entitiesIn(sl, zone)) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().mobAttack(this), 10F)) {
                Vec3 push = e.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.8);
                e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.6, push.z));
                e.hurtMarked = true;
            }
        }
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, HmRegistries.HOLLOW_SAND.get().defaultBlockState()),
                getX(), getY() + 0.2, getZ(), 40, 1.8, 0.2, 1.8, 0.2);
        HmFx.ring(sl, position().add(0, 0.2, 0), 3.5, 28, ParticleTypes.CLOUD, 0.05);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 0.9F, 0.7F);
        BossKit.shake(sl, position(), 14, 0.7F, 10);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        action("punch");
        return super.doHurtTarget(target);
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.8F;
    }
}
