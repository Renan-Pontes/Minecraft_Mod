package com.renanpontes.isekaimahou.feature.fire_water.entity;

import java.util.EnumSet;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.fire_water.FwRegistries;
import com.renanpontes.isekaimahou.feature.fire_water.FwUtil;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Salamandra invocada (Invocar Salamandra): lagarto de fogo aliado que cospe um jato de chamas em cone
 * e morde quem chega perto. Imune a fogo, fraca contra água.
 */
public class SummonedSalamander extends FwSummon {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.summoned_salamander.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.summoned_salamander.walk");
    private static final RawAnimation BREATHE = RawAnimation.begin().thenPlay("animation.summoned_salamander.breathe");

    public SummonedSalamander(EntityType<? extends SummonedSalamander> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new FireBreathGoal(this));
        goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.25, 7.0F, 2.5F));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        addTargetGoals();
    }

    /** Criatura de fogo: não pega as queimaduras mágicas. */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.is(FwRegistries.LINGERING_EMBER) || effect.is(FwRegistries.AZURE_FLAME)) return false;
        return super.canBeAffected(effect);
    }

    /** Mordida que também queima. */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) target.igniteForSeconds(3.0F);
        return hit;
    }

    // =====================================================================================
    //  Visual e som
    // =====================================================================================

    @Override
    protected void clientFx() {
        if (tickCount % 3 != 0) return;
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        // Chama na ponta da cauda e brasas nas costas
        level().addParticle(Vfx.flame(), getX() - fx * 1.45, getY() + 0.34, getZ() - fz * 1.45, 0, 0.02, 0);
        if (random.nextInt(2) == 0) {
            level().addParticle(Vfx.smallFlame(), getX() + (random.nextDouble() - 0.5) * 0.3, getY() + 0.62,
                    getZ() + (random.nextDouble() - 0.5) * 0.3, 0, 0.015, 0);
        }
        if (random.nextInt(12) == 0) level().addParticle(ParticleTypes.LAVA, getX(), getY() + 0.5, getZ(), 0, 0, 0);
    }

    @Override
    protected void spawnFx(ServerLevel level) {
        Vec3 c = position().add(0, 0.3, 0);
        FwUtil.shockwave(level, c, Vfx.flame(), 28, 0.18);
        level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 8, 0.4, 0.2, 0.4, 0);
        level.sendParticles(Vfx.largeSmoke(), c.x, c.y + 0.3, c.z, 10, 0.4, 0.3, 0.4, 0.02);
        FwUtil.sound(level, c, SoundEvents.BLAZE_SHOOT, 1.0F, 0.8F);
        FwUtil.sound(level, c, SoundEvents.FIRECHARGE_USE, 0.8F, 1.0F);
    }

    @Override
    protected void vanishFx(ServerLevel level) {
        level.sendParticles(Vfx.flame(), getX(), getY() + 0.3, getZ(), 20, 0.4, 0.2, 0.6, 0.05);
        level.sendParticles(Vfx.smoke(), getX(), getY() + 0.4, getZ(), 12, 0.4, 0.2, 0.6, 0.02);
        FwUtil.sound(level, position(), SoundEvents.FIRE_EXTINGUISH, 0.7F, 1.2F);
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.STRIDER_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.STRIDER_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.STRIDER_DEATH; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.STRIDER_STEP, 0.3F, 1.4F);
    }

    @Override
    public float getVoicePitch() { return super.getVoicePitch() * 1.35F; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5,
                state -> state.setAndContinue(state.isMoving() ? WALK : IDLE)));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("breathe", BREATHE));
    }

    // =====================================================================================
    //  Ataque: sopro de fogo em cone + mordida
    // =====================================================================================

    static class FireBreathGoal extends Goal {
        private static final double RANGE = 5.5;
        private static final double CONE_COS = Math.cos(Math.toRadians(28));
        private final SummonedSalamander mob;
        private int breathTicks;
        private int cooldown = 10;
        private int biteCooldown;

        FireBreathGoal(SummonedSalamander mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = mob.getTarget();
            return t != null && t.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return breathTicks > 0 || canUse();
        }

        @Override
        public void stop() {
            breathTicks = 0;
            mob.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = mob.getTarget();
            if (cooldown > 0) cooldown--;
            if (biteCooldown > 0) biteCooldown--;
            if (t == null) {
                if (breathTicks > 0) breathTicks--;
                return;
            }
            mob.getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (breathTicks > 0) {
                breathTicks--;
                mob.getNavigation().stop();
                breathe(t);
                return;
            }
            double d = mob.distanceToSqr(t);
            boolean sees = mob.getSensing().hasLineOfSight(t);
            if (d > (RANGE - 1) * (RANGE - 1) || !sees) {
                mob.getNavigation().moveTo(t, 1.3);
                return;
            }
            if (cooldown <= 0) {
                mob.getNavigation().stop();
                breathTicks = 24;
                cooldown = 60;
                mob.triggerAnim("action", "breathe");
                FwUtil.sound((ServerLevel) mob.level(), mob.position(), SoundEvents.BLAZE_SHOOT, 1.0F, 1.1F);
            } else if (d < 2.4 * 2.4 && biteCooldown <= 0) {
                biteCooldown = 20;
                mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                mob.doHurtTarget(t);
            } else if (d > 2.0 * 2.0) {
                mob.getNavigation().moveTo(t, 1.1);
            }
        }

        /** Só queima o alvo, monstros e quem está atacando o grupo (poupa animais e aldeões no caminho). */
        private boolean isFoe(LivingEntity e, LivingEntity target) {
            if (e == target || e instanceof net.minecraft.world.entity.monster.Enemy) return true;
            return e instanceof Mob m && m.getTarget() != null && (m.getTarget() == mob || mob.isAlliedTo(m.getTarget()));
        }

        private void breathe(LivingEntity t) {
            ServerLevel sl = (ServerLevel) mob.level();
            float yaw = mob.yHeadRot * Mth.DEG_TO_RAD;
            Vec3 mouth = mob.position().add(-Mth.sin(yaw) * 0.95, 0.3, Mth.cos(yaw) * 0.95);
            Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(mouth).normalize();
            // Jato de chamas: partículas com velocidade na direção do alvo
            for (int i = 0; i < 6; i++) {
                double jx = (sl.random.nextDouble() - 0.5) * 0.3, jy = (sl.random.nextDouble() - 0.5) * 0.25,
                        jz = (sl.random.nextDouble() - 0.5) * 0.3;
                sl.sendParticles(i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME, mouth.x, mouth.y, mouth.z, 0,
                        aim.x + jx, aim.y + jy, aim.z + jz, 0.32 + sl.random.nextDouble() * 0.12);
            }
            if (breathTicks % 4 == 0) {
                sl.sendParticles(Vfx.smoke(), mouth.x, mouth.y, mouth.z, 0, aim.x, aim.y + 0.2, aim.z, 0.15);
                FwUtil.sound(sl, mouth, SoundEvents.FIRE_AMBIENT, 0.8F, 1.3F);
            }
            if (breathTicks % 6 != 0) return;
            LivingEntity owner = mob.getOwner();
            float dmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6F;
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(RANGE),
                    e -> e != mob && e.isAlive() && !mob.isAlliedTo(e) && isFoe(e, t))) {
                Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(mouth);
                double dist = to.length();
                if (dist > RANGE || dist < 1.0E-3 || to.scale(1.0 / dist).dot(aim) < CONE_COS) continue;
                if (!mob.hasLineOfSight(e)) continue;
                float mult = ElementReactions.multiplier(e.getType(), Element.FIRE);
                if (mult > 0) {
                    ElementReactions.mark(e, Element.FIRE, owner != null ? owner.getUUID() : null, sl.getGameTime());
                    e.invulnerableTime = 0;
                    e.hurt(FwUtil.summonSpellSource(mob, owner), dmg * mult);
                }
                e.igniteForSeconds(3.0F);
            }
        }
    }
}
