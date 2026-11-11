package com.renanpontes.isekaimahou.feature.class_ranger.entity;

import java.util.EnumSet;

import com.renanpontes.isekaimahou.feature.class_ranger.RangerCompanions;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerFx;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerMarks;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Águia Batedora do Ranger (Trilha do Ranger rank I): voa em círculos sobre o dono e, a cada poucos segundos,
 * BATE O TERRENO — o monstro mais próximo (até 24 blocos) ganha a Marca do Caçador (brilha através das paredes e leva
 * mais dano do dono e dos companheiros). Mergulha com as garras nos alvos. Cresce com a classe.
 */
public class RangerEagleEntity extends RangerCompanion implements FlyingAnimal {
    public static final String ID = "ranger_eagle";
    public static final int SCOUT_RGB = 0xFFC94A;
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.ranger_eagle.fly");
    private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.ranger_eagle.glide");
    private static final RawAnimation PERCH = RawAnimation.begin().thenLoop("animation.ranger_eagle.perch");
    private static final RawAnimation DIVE = RawAnimation.begin().thenPlay("animation.ranger_eagle.dive");
    private static final RawAnimation SCREECH = RawAnimation.begin().thenPlay("animation.ranger_eagle.screech");

    private int scoutCooldown = 40;

    public RangerEagleEntity(EntityType<? extends RangerEagleEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 18.0)
                .add(Attributes.FLYING_SPEED, 0.95)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.5)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected double healthPerLevel() { return 5.0; }

    @Override
    protected double damagePerLevel() { return 1.1; }

    @Override
    public String slotKey() { return RangerCompanions.EAGLE; }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    public boolean isFlying() {
        return !onGround();
    }

    @Override
    public boolean causeFallDamage(float dist, float mult, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal(this));
        goalSelector.addGoal(2, new DiveGoal());
        goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.3, 8.0F, 3.0F));
        goalSelector.addGoal(7, new WaterAvoidingRandomFlyingGoal(this, 0.9));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
        targetSelector.addGoal(0, new MarkedTargetGoal());
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false, this::huntable));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl) || !(getOwner() instanceof LivingEntity owner)) return;
        if (--scoutCooldown > 0) return;
        // Batida: marca o monstro mais próximo do dono que ainda não está marcado (o Alfa vê mais longe)
        scoutCooldown = 80 - 10 * stage();
        double range = 20 + 4 * stage();
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(owner.blockPosition()).inflate(range),
                e -> e instanceof Enemy && e.isAlive() && !RangerUtil.isFriend(owner, e))) {
            if (RangerMarks.isMarkedBy(e, owner.getUUID())) continue;
            double d = e.distanceToSqr(owner);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best == null) return;
        RangerMarks.mark(best, owner, 120 + 40 * stage(), SCOUT_RGB, 0.15F + 0.05F * stage());
        screech();
        if (getTarget() == null) setTarget(best);
    }

    @Override
    public void command(LivingEntity target) {
        super.command(target);
        screech();
    }

    /** Grito agudo (ao chamar e ao marcar). */
    public void screech() {
        triggerAnim("action", "screech");
        if (level() instanceof ServerLevel sl) RangerFx.sound(sl, position(), SoundEvents.PARROT_IMITATE_PHANTOM, 1.0F, 1.5F + 0.1F * stage());
    }

    /** Circula alto sobre o alvo e mergulha; o golpe deixa o alvo marcado e (Adulto+) cego por um instante. */
    private final class DiveGoal extends Goal {
        private int cooldown;

        DiveGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return targetValid();
        }

        @Override
        public boolean canContinueToUse() {
            return targetValid();
        }

        @Override
        public void start() {
            cooldown = 10;
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().setLookAt(t, 30F, 30F);
            if (cooldown > 0) {
                cooldown--;
                double a = tickCount * 0.15;
                getMoveControl().setWantedPosition(t.getX() + Math.cos(a) * 3.5, t.getY() + 5.5, t.getZ() + Math.sin(a) * 3.5, 1.2);
                return;
            }
            Vec3 aim = t.position().add(0, t.getBbHeight() * 0.6, 0);
            getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, 2.2);
            if (distanceToSqr(aim) < 2.8) {
                if (doHurtTarget(t)) {
                    if (getOwner() instanceof LivingEntity o) RangerMarks.mark(t, o, 100, SCOUT_RGB, 0.15F + 0.05F * stage());
                    if (stage() >= 1) t.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.BLINDNESS, 30, 0), RangerEagleEntity.this);
                    if (level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.7, t.getZ(), 8, 0.2, 0.2, 0.2, 0.15);
                        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 4, 0.2, 0.2, 0.2, 0.05);
                    }
                }
                triggerAnim("action", "dive");
                setDeltaMovement(getDeltaMovement().add(0, 0.7, 0));
                cooldown = 30 - 4 * stage();
            }
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PARROT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PARROT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PARROT_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.65F;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<RangerEagleEntity>(this, "movement", 4, state -> {
            if (onGround() && !state.isMoving()) return state.setAndContinue(PERCH);
            return state.setAndContinue(getDeltaMovement().y < -0.05 ? GLIDE : FLY);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("dive", DIVE).triggerableAnim("screech", SCREECH));
    }
}
