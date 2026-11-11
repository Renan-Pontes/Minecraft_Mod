package com.renanpontes.isekaimahou.feature.origins_b.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.origins_b.ObFx;
import com.renanpontes.isekaimahou.feature.origins_b.ObUtil;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Demônio Maior: brutamontes infernal de 3 blocos. Garras pesadas (Fogo), Pisão Infernal em área quando cercado
 * e rajada de 3 bolas de fogo em quem foge do alcance. Emerge de um portal de chamas ao ser invocado.
 */
public class SummonedDemonEntity extends ObSummonEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.summoned_demon.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.summoned_demon.walk");
    private static final RawAnimation EMERGE = RawAnimation.begin().thenPlay("animation.summoned_demon.emerge");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.summoned_demon.attack");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("animation.summoned_demon.slam");
    private static final RawAnimation VOLLEY = RawAnimation.begin().thenPlay("animation.summoned_demon.volley");

    private int slamCooldown = 60;
    private int slamWindup;
    private int volleyCooldown = 40;
    private int volleyShots;

    public SummonedDemonEntity(EntityType<? extends SummonedDemonEntity> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 120.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.85)
                .add(Attributes.STEP_HEIGHT, 1.5)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(3, new FollowOwnerGoal(1.1, 6.0F, 3.0F));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7) {
            @Override
            public boolean canUse() {
                return getOwnerUUID() == null && super.canUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        addTargetGoals();
    }

    @Override
    protected Element attackElement() {
        return Element.FIRE;
    }

    @Override
    protected int arrivalLength() {
        return 30;
    }

    /** Sai de um portal de chamas: círculo no chão e colunas de fogo. */
    @Override
    protected void arrivalTick(ServerLevel level, int ticksLeft) {
        if (ticksLeft % 3 == 0) ObFx.pactCircle(level, position(), 1.8);
        level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.2, getZ(), 6, 0.7, 0.05, 0.7, 0.06);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 3, 0.6, 0.8, 0.6, 0.02);
        if (ticksLeft == 30) level.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, getSoundSource(), 0.6F, 1.4F);
        if (ticksLeft == 8) level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, getSoundSource(), 1.4F, 0.6F);
    }

    // ------------------------------------------------------------------ habilidades
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (isRemoved()) return;
        ServerLevel sl = (ServerLevel) level();
        LivingEntity target = getTarget();

        // Pisão Infernal: anima, espera o impacto e queima tudo em volta
        if (slamWindup > 0) {
            getNavigation().stop();
            if (--slamWindup == 0) slamImpact(sl);
            return;
        }
        if (slamCooldown > 0) slamCooldown--;
        if (volleyCooldown > 0) volleyCooldown--;
        if (target == null) return;

        double d2 = distanceToSqr(target);
        if (slamCooldown <= 0 && d2 < 4.5 * 4.5 && countEnemiesNear(4.5) >= 1 && random.nextInt(3) == 0) {
            slamWindup = 12;
            slamCooldown = 120 + random.nextInt(40);
            triggerAnim("action", "slam");
            playSound(SoundEvents.RAVAGER_ROAR, 1.0F, 0.8F);
            return;
        }
        // Rajada: alvo longe do alcance das garras
        if (volleyShots > 0) {
            if (tickCount % 5 == 0) {
                shootVolley(sl, target);
                volleyShots--;
            }
        } else if (volleyCooldown <= 0 && d2 > 6 * 6 && d2 < 20 * 20 && hasLineOfSight(target)) {
            volleyShots = 3;
            volleyCooldown = 100 + random.nextInt(40);
            triggerAnim("action", "volley");
            playSound(SoundEvents.BLAZE_AMBIENT, 1.0F, 0.5F);
        }
    }

    private int countEnemiesNear(double r) {
        return level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r), e -> e != this && e.isAlive() && !isFriend(e)).size();
    }

    private void slamImpact(ServerLevel sl) {
        double r = 4.5;
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.75F * power();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r, 1.5, r),
                e -> e != this && e.isAlive() && !isFriend(e) && e.distanceToSqr(this) < r * r + 4)) {
            if (ObUtil.spellHurt(sl, e, dmg, this, getOwner(), Element.FIRE)) {
                e.igniteForSeconds(4.0F);
                if (e.isAlive()) e.setLastHurtByMob(this);
                Vec3 push = e.position().subtract(position()).multiply(1, 0, 1);
                if (push.lengthSqr() < 1.0E-4) push = new Vec3(1, 0, 0);
                push = push.normalize().scale(1.1);
                e.push(push.x, 0.55, push.z);
                e.hurtMarked = true;
            }
        }
        // Onda de fogo no chão
        for (int ring = 1; ring <= 3; ring++) {
            double rr = ring * 1.4;
            int pts = ring * 14;
            for (int i = 0; i < pts; i++) {
                double a = Math.PI * 2 * i / pts;
                sl.sendParticles(ParticleTypes.FLAME, getX() + Math.cos(a) * rr, getY() + 0.1, getZ() + Math.sin(a) * rr, 1, 0.05, 0.05, 0.05, 0.03);
                if (i % 3 == 0) sl.sendParticles(ParticleTypes.LAVA, getX() + Math.cos(a) * rr, getY() + 0.1, getZ() + Math.sin(a) * rr, 1, 0, 0, 0, 0);
            }
        }
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.3, getZ(), 1, 0, 0, 0, 0);
        ObFx.groundCrack(sl, this);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), getSoundSource(), 1.3F, 0.7F);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, getSoundSource(), 0.8F, 0.5F);
    }

    private void shootVolley(ServerLevel sl, LivingEntity target) {
        Vec3 from = position().add(0, getBbHeight() * 0.8, 0).add(getLookAngle().scale(1.0));
        Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from).normalize();
        dir = dir.add(random.nextGaussian() * 0.05, random.nextGaussian() * 0.03, random.nextGaussian() * 0.05).normalize();
        ImpFireballEntity ball = new ImpFireballEntity(sl, this, dir, 7.0F * power(), true);
        ball.setPos(from.x, from.y, from.z);
        sl.addFreshEntity(ball);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GHAST_SHOOT, getSoundSource(), 0.8F, 0.6F);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = super.doHurtTarget(target);
        if (ok) triggerAnim("action", "attack");
        return ok;
    }

    @Override
    protected void onMeleeHit(ServerLevel level, Entity target) {
        target.igniteForSeconds(4.0F);
        level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 12, 0.3, 0.3, 0.3, 0.06);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 6, 0.3, 0.3, 0.3, 0.2);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ATTACK, getSoundSource(), 1.0F, 0.8F);
    }

    // ------------------------------------------------------------------ visual
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // Brasas subindo dos ombros e chifres
            if (random.nextInt(2) == 0) {
                double yaw = Math.toRadians(yBodyRot);
                double side = random.nextBoolean() ? 0.55 : -0.55;
                level().addParticle(ParticleTypes.SMALL_FLAME, getX() + Math.cos(yaw) * side, getY() + 2.25, getZ() + Math.sin(yaw) * side,
                        0, 0.02, 0);
            }
            if (random.nextInt(4) == 0) {
                level().addParticle(ParticleTypes.LARGE_SMOKE, getX() + (random.nextDouble() - 0.5), getY() + 0.2 + random.nextDouble() * 2.5,
                        getZ() + (random.nextDouble() - 0.5), 0, 0.02, 0);
            }
        }
    }

    @Override
    protected void dissolveFx(ServerLevel level) {
        ObFx.hellfireBurst(level, position(), 2.2);
        ObFx.pactCircle(level, position(), 1.6);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_DEATH, getSoundSource(), 1.0F, 0.5F);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl) ObFx.hellfireBurst(sl, position(), 2.2);
        super.die(source);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BLAZE_AMBIENT;
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
    public float getVoicePitch() {
        return 0.55F + random.nextFloat() * 0.1F;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 0.6F, 0.7F);
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (arrivalTicks() > 0) return state.setAndContinue(EMERGE);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK).triggerableAnim("slam", SLAM).triggerableAnim("volley", VOLLEY));
    }
}
