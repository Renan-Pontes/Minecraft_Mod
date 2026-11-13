package com.renanpontes.isekaimahou.feature.origins_a.familiar;

import java.util.EnumSet;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Gato familiar (preto, olhos verdes): segue o dono, arranha quem o atacar e ASSUSTA creepers e phantoms —
 * creepers desarmam e fogem, phantoms desistem do mergulho. Creepers também evitam o gato de longe.
 */
public class FamiliarCatEntity extends FamiliarEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.familiar_cat.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.familiar_cat.walk");
    private static final RawAnimation SIT = RawAnimation.begin().thenLoop("animation.familiar_cat.sit");
    private static final RawAnimation HISS = RawAnimation.begin().thenPlay("animation.familiar_cat.hiss");
    private static final RawAnimation SCRATCH = RawAnimation.begin().thenPlay("animation.familiar_cat.scratch");
    public static final double SCARE_RANGE = 10.0;

    private int hissCooldown;

    public FamiliarCatEntity(EntityType<? extends FamiliarCatEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .add(Attributes.SAFE_FALL_DISTANCE, 8.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.35, true));
        goalSelector.addGoal(3, new FollowOwnerGoal(this));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return owner == null && super.canUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        // Revida quem o arranhar (creepers ele só espanta; o dono nunca o machuca, ver FamiliarEntity.hurt)
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Creeper.class));
    }

    // =====================================================================================
    //  IA (servidor)
    // =====================================================================================
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (tickLife(level)) return;
        LivingEntity o = getOwner();
        keepUp(level, o, 20, 0.0);
        if (hissCooldown > 0) hissCooldown--;
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || (o != null && t.distanceToSqr(o) > 20 * 20) || isAlliedTo(t))) setTarget(null);
        if (tickCount % 10 == 0) {
            scare(level, o);
            defend(o);
        }
    }

    /** Afasta creepers e phantoms que estejam ameaçando o gato, o dono ou aliados. */
    private void scare(ServerLevel level, LivingEntity o) {
        boolean hissed = false;
        Vec3 center = o != null && o.distanceToSqr(this) < 64 ? o.position() : position();
        for (Creeper c : level.getEntitiesOfClass(Creeper.class, getBoundingBox().inflate(SCARE_RANGE))) {
            LivingEntity t = c.getTarget();
            boolean threat = c.getSwellDir() > 0 || (t != null && (t == this || isAlliedTo(t) || t == o));
            if (!threat && c.distanceToSqr(this) > 36) continue;
            c.setSwellDir(-1);
            c.setTarget(null);
            Vec3 away = DefaultRandomPos.getPosAway(c, 16, 7, center);
            if (away != null) c.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
            hissed = true;
        }
        for (Phantom p : level.getEntitiesOfClass(Phantom.class, getBoundingBox().inflate(SCARE_RANGE * 2, 24, SCARE_RANGE * 2))) {
            LivingEntity t = p.getTarget();
            if (t == null || !(t == this || t == o || isAlliedTo(t))) continue;
            p.setTarget(null);
            Vec3 up = p.position().subtract(center).normalize().scale(0.8).add(0, 0.8, 0);
            p.setDeltaMovement(up);
            p.hurtMarked = true;
            hissed = true;
        }
        if (hissed) hiss(level);
    }

    /** Arranha quem atacou o dono (menos creepers: esses ele só espanta). */
    private void defend(LivingEntity o) {
        if (o == null || getTarget() != null) return;
        LivingEntity a = o.getLastHurtByMob();
        if (a != null && a.isAlive() && !(a instanceof Creeper) && !isAlliedTo(a) && o.tickCount - o.getLastHurtByMobTimestamp() < 100
                && a.distanceToSqr(this) < 16 * 16) {
            setTarget(a);
        }
    }

    private void hiss(ServerLevel level) {
        if (hissCooldown > 0) return;
        hissCooldown = 40;
        triggerAnim("action", "hiss");
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.CAT_HISS, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 0.9, getZ(), 2, 0.2, 0.1, 0.2, 0);
        level.sendParticles(Vfx.witch(), getX(), getY() + 0.5, getZ(), 10, 0.4, 0.3, 0.4, 0.05);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim("action", "scratch");
        boolean hit = super.doHurtTarget(target);
        if (hit) playSound(SoundEvents.CAT_HISS, 0.5F, 1.4F);
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return getTarget() != null ? SoundEvents.CAT_HISS : (random.nextInt(4) == 0 ? SoundEvents.CAT_PURR : SoundEvents.CAT_AMBIENT);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CAT_DEATH;
    }

    // =====================================================================================
    //  Seguir o dono
    // =====================================================================================
    static class FollowOwnerGoal extends Goal {
        private final FamiliarCatEntity cat;
        private int repath;

        FollowOwnerGoal(FamiliarCatEntity cat) {
            this.cat = cat;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity o = cat.getOwner();
            return o != null && cat.getTarget() == null && cat.distanceToSqr(o) > 5 * 5;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity o = cat.getOwner();
            return o != null && cat.getTarget() == null && !cat.getNavigation().isDone() && cat.distanceToSqr(o) > 2.5 * 2.5;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            cat.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity o = cat.getOwner();
            if (o == null) return;
            cat.getLookControl().setLookAt(o, 10.0F, cat.getMaxHeadXRot());
            if (--repath <= 0) {
                repath = 10;
                cat.getNavigation().moveTo(o, cat.distanceToSqr(o) > 12 * 12 ? 1.5 : 1.15);
            }
        }
    }

    // =====================================================================================
    //  Animação (GeckoLib)
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (state.isMoving()) return state.setAndContinue(WALK);
            return state.setAndContinue(tickCount % 400 < 200 && getTarget() == null ? SIT : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("hiss", HISS).triggerableAnim("scratch", SCRATCH));
    }
}
