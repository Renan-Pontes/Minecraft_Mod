package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Ogro: enorme e lento. Não dá socos — levanta o porrete com as duas mãos (aviso visível) e esmaga o chão à
 * frente, acertando todos os inimigos na área. Ruge quando avista uma presa.
 */
public class OgreEntity extends CbMonster {
    private static final int WINDUP = 17;
    private static final int TOTAL = 26;

    public OgreEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        xpReward = 18;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.MOVEMENT_SPEED, 0.23)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.STEP_HEIGHT, 1.2)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public CbFaction faction() {
        return CbFaction.HORDE;
    }

    @Override
    protected String animId() {
        return "ogre";
    }

    @Override
    protected String[] actionAnims() {
        return new String[] {"slam", "roar"};
    }

    @Override
    protected float voicePitch() {
        return 0.6F;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, busyGoal());
        goalSelector.addGoal(2, new ClubSlamGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
    }

    @Override
    public void setTarget(LivingEntity target) {
        LivingEntity old = getTarget();
        super.setTarget(target);
        // Ruge ao achar uma presa nova.
        if (!level().isClientSide && target instanceof Player && old == null && isAlive()) {
            playAction("roar");
            playSound(SoundEvents.RAVAGER_ROAR, 1.6F, 0.75F);
            setBusy(20);
        }
    }

    /** Persegue o alvo; perto, levanta o porrete e esmaga (dano em área à frente). */
    private class ClubSlamGoal extends Goal {
        private int t = -1;
        private int cooldown;
        private int repath;

        ClubSlamGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return target != null && target.isAlive() && !isBusy();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = getTarget();
            return isAlive() && !isBusy() && (t >= 0 || (target != null && target.isAlive()));
        }

        @Override
        public void start() {
            t = -1;
            repath = 0;
            setAggressive(true);
        }

        @Override
        public void stop() {
            t = -1;
            setAggressive(false);
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (cooldown > 0) cooldown--;
            if (t >= 0) {
                swingTick(target);
                return;
            }
            if (target == null) return;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            double reach = 3.2 + getBbWidth() * 0.5 + target.getBbWidth() * 0.5;
            if (distanceToSqr(target) <= reach * reach) {
                getNavigation().stop();
                if (cooldown <= 0) {
                    t = 0;
                    playAction("slam");
                    playSound(SoundEvents.RAVAGER_ATTACK, 1.2F, 0.6F);
                }
            } else if (--repath <= 0) {
                repath = 8;
                getNavigation().moveTo(target, 1.0);
            }
        }

        private void swingTick(LivingEntity target) {
            t++;
            if (t < WINDUP - 4 && target != null) CbFx.face(OgreEntity.this, target);
            if (t == WINDUP) {
                Vec3 c = CbFx.ahead(OgreEntity.this, 2.4);
                float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
                CbFx.shockwave(OgreEntity.this, c, 2.8, dmg, 0.9, 0.55);
                CbFx.sound(OgreEntity.this, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.55F);
                CbFx.sound(OgreEntity.this, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 1.0F, 0.6F);
            }
            if (t >= TOTAL) {
                t = -1;
                cooldown = 22;
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 0.6F, 0.8F);
    }
}
