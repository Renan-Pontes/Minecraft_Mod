package com.renanpontes.isekaimahou.feature.hueco_mundo.entity;

import java.util.List;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Cão de Espinhos: lobo de galhos e roseira, guardião da Corte das Fadas. A mordida enraíza por um instante e quem bate
 * nele com a mão leva espinhos de volta.
 */
public class BrambleHound extends HmMob {
    private int rootCd;

    public BrambleHound(EntityType<? extends BrambleHound> type, Level level) {
        super(type, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 32).add(Attributes.ARMOR, 4)
                .add(Attributes.MOVEMENT_SPEED, 0.34).add(Attributes.ATTACK_DAMAGE, 6).add(Attributes.FOLLOW_RANGE, 32)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    public String modelId() {
        return "bramble_hound";
    }

    @Override
    public HmRealm side() {
        return HmRealm.FEY;
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("bite");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (rootCd > 0) rootCd--;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        action("bite");
        boolean hit = super.doHurtTarget(target);
        if (hit && rootCd == 0 && target instanceof LivingEntity le) {
            rootCd = 100;
            le.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, 25, 0), this);
            if (level() instanceof ServerLevel sl) sl.sendParticles(HmFx.dust(HmFx.THORN, 1.2F), le.getX(), le.getY() + 0.2, le.getZ(), 12, 0.4, 0.1, 0.4, 0);
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        // Espinhos: só golpes diretos (sem projétil) devolvem dano.
        if (r && source.getDirectEntity() instanceof LivingEntity attacker && source.getDirectEntity() == source.getEntity() && attacker != this) {
            attacker.hurt(damageSources().thorns(this), 1.5F);
        }
        return r;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WOLF_GROWL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SWEET_BERRY_BUSH_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WOLF_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F;
    }
}
