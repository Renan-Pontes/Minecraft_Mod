package com.renanpontes.isekaimahou.feature.creatures_c.entity;

import java.util.EnumSet;

import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Harpia: voa pelas montanhas e circula a presa; sobe, mergulha num RASANTE com as garras e volta a subir.
 * De vez em quando dá um GRITO estridente que deixa lento quem estiver perto.
 */
public class Harpy extends Monster implements FlyingAnimal, GeoEntity {
    private static final EntityDataAccessor<Boolean> DATA_DIVING = SynchedEntityData.defineId(Harpy.class, EntityDataSerializers.BOOLEAN);
    private static final double SCREECH_RADIUS = 8.0;

    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.harpy.fly");
    private static final RawAnimation HOVER = RawAnimation.begin().thenLoop("animation.harpy.hover");
    private static final RawAnimation DIVE = RawAnimation.begin().thenLoop("animation.harpy.dive");
    private static final RawAnimation PERCH = RawAnimation.begin().thenLoop("animation.harpy.perch");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.harpy.walk");
    private static final RawAnimation CLAW = RawAnimation.begin().thenPlay("animation.harpy.claw");
    private static final RawAnimation SCREECH = RawAnimation.begin().thenPlay("animation.harpy.screech");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int swoopCooldown = 40;
    private int screechCooldown = 100;

    public Harpy(EntityType<? extends Harpy> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.xpReward = 10;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                // Voo no estilo do Allay: velocidade final ≈ 10 × (FLYING_SPEED × modificador)² blocos/tick
                // (0.2 → ~0.4 b/t circulando; valores como 0.6 dariam >70 m/s)
                .add(Attributes.FLYING_SPEED, 0.2)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DIVING, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ScreechGoal());
        goalSelector.addGoal(2, new SwoopGoal());
        goalSelector.addGoal(3, new CircleTargetGoal());
        goalSelector.addGoal(6, new WaterAvoidingRandomFlyingGoal(this, 1.0));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    public boolean isDiving() {
        return entityData.get(DATA_DIVING);
    }

    private void setDiving(boolean diving) {
        entityData.set(DATA_DIVING, diving);
    }

    // =====================================================================================
    //  Voo
    // =====================================================================================

    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance()) {
            if (isStunned()) {
                // Atordoada/petrificada: para de bater as asas e despenca
                input = Vec3.ZERO;
                setDeltaMovement(getDeltaMovement().add(0.0, -0.06, 0.0));
            }
            if (isInWater()) {
                moveRelative(0.02F, input);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.8));
            } else {
                moveRelative(getSpeed(), input);
                move(MoverType.SELF, getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(0.91));
            }
        }
        calculateEntityAnimation(false);
    }

    /** Atordoado (núcleo) zera MOVEMENT_SPEED, mas o voo usa FLYING_SPEED: tratamos aqui. */
    private boolean isStunned() {
        return hasEffect(CoreRegistries.STUNNED);
    }

    @Override
    public boolean isFlying() {
        return !onGround();
    }

    /**
     * Harpias não ligam para a luz. O padrão do Monster dá valor negativo em lugares claros, o que faz
     * {@code checkSpawnRules} recusar todo spawn natural de dia (e a regra de spawn delas aceita qualquer luz).
     */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide) {
            if (swoopCooldown > 0) swoopCooldown--;
            if (screechCooldown > 0) screechCooldown--;
        } else if (isDiving() && tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.CLOUD, getX(), getY() + 0.8, getZ(), 0, 0, 0);
        }
    }

    // =====================================================================================
    //  Combate
    // =====================================================================================

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            triggerAnim("action", "claw");
            playSound(SoundEvents.PHANTOM_BITE, 1.0F, 1.3F);
        }
        return hit;
    }

    /** Grito estridente: Lentidão II em quem estiver no raio (menos outras harpias), com anel de ar. */
    private void screech(ServerLevel sl) {
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_AMBIENT, SoundSource.HOSTILE, 2.5F, 1.7F);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GOAT_SCREAMING_AMBIENT, SoundSource.HOSTILE, 1.6F, 1.5F);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(SCREECH_RADIUS),
                e -> e != this && e.isAlive() && !(e instanceof Harpy) && !isAlliedTo(e)
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))) {
            if (e.distanceToSqr(this) > SCREECH_RADIUS * SCREECH_RADIUS) continue;
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1), this);
        }
        double y = getY() + 1.2;
        for (int ring = 0; ring < 2; ring++) {
            for (int i = 0; i < 28; i++) {
                double a = Math.PI * 2 * i / 28;
                sl.sendParticles(ParticleTypes.CLOUD, getX(), y + ring * 0.4, getZ(), 0, Math.cos(a), 0.0, Math.sin(a), 0.45 + ring * 0.15);
            }
        }
        sl.sendParticles(ParticleTypes.POOF, getX(), y, getZ(), 8, 0.3, 0.3, 0.3, 0.05);
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PHANTOM_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PHANTOM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.35F;
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    public void tick() {
        super.tick();
        // Bater de asas ocasional (só som, o visual vem da animação)
        if (level().isClientSide && !onGround() && tickCount % 24 == 0) {
            level().playLocalSound(getX(), getY(), getZ(), SoundEvents.PHANTOM_FLAP, getSoundSource(), 0.35F,
                    1.1F + random.nextFloat() * 0.2F, false);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("SwoopCooldown", swoopCooldown);
        tag.putInt("ScreechCooldown", screechCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        swoopCooldown = tag.getInt("SwoopCooldown");
        screechCooldown = tag.getInt("ScreechCooldown");
    }

    // =====================================================================================
    //  Animações
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> {
            if (isDiving()) return state.setAndContinue(DIVE);
            if (onGround()) return state.setAndContinue(state.isMoving() ? WALK : PERCH);
            return state.setAndContinue(state.isMoving() ? FLY : HOVER);
        }));
        controllers.add(new AnimationController<>(this, "action", 0, state -> PlayState.STOP)
                .triggerableAnim("claw", CLAW)
                .triggerableAnim("screech", SCREECH));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // =====================================================================================
    //  IA
    // =====================================================================================

    /** Pára no ar, abre as asas e grita. */
    class ScreechGoal extends Goal {
        private int windup;

        ScreechGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && screechCooldown <= 0 && !isDiving() && !isStunned() && distanceToSqr(t) < 144.0
                    && hasLineOfSight(t) && random.nextInt(15) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return windup > 0 && isAlive();
        }

        @Override
        public void start() {
            windup = 16;
            getNavigation().stop();
            triggerAnim("action", "screech");
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t != null) getLookControl().setLookAt(t, 30.0F, 30.0F);
            setDeltaMovement(getDeltaMovement().scale(0.7));
            if (--windup == 0 && level() instanceof ServerLevel sl) screech(sl);
        }

        @Override
        public void stop() {
            screechCooldown = 160 + random.nextInt(100);
        }
    }

    /** Rasante: sobe acima do alvo, mergulha com as garras e se afasta subindo. */
    class SwoopGoal extends Goal {
        private static final int RISE = 0, DIVE = 1, RECOVER = 2;
        private int phase;
        private int timer;
        private Vec3 waypoint = Vec3.ZERO;

        SwoopGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && swoopCooldown <= 0 && !isStunned() && distanceToSqr(t) < 24.0 * 24.0;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity t = getTarget();
            return t != null && t.isAlive() && !isStunned() && phase <= RECOVER && !(phase == RECOVER && timer > 25);
        }

        @Override
        public boolean isInterruptable() {
            return phase != DIVE;
        }

        @Override
        public void start() {
            phase = RISE;
            timer = 0;
            LivingEntity t = getTarget();
            if (t != null) {
                // Ponto alto ao lado do alvo, no lado em que a harpia já está
                Vec3 side = position().subtract(t.position()).multiply(1, 0, 1);
                side = side.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : side.normalize();
                waypoint = t.position().add(side.scale(6.0)).add(0, 6.0, 0);
            }
        }

        @Override
        public void stop() {
            setDiving(false);
            swoopCooldown = 50 + random.nextInt(50);
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            timer++;
            getLookControl().setLookAt(t, 30.0F, 30.0F);
            if (phase == RISE) {
                getMoveControl().setWantedPosition(waypoint.x, waypoint.y, waypoint.z, 1.3);
                if ((position().distanceToSqr(waypoint) < 4.0 || timer > 60) && hasLineOfSight(t)) {
                    phase = DIVE;
                    timer = 0;
                    setDiving(true);
                    playSound(SoundEvents.PHANTOM_SWOOP, 1.6F, 1.2F);
                } else if (timer > 120) {
                    // Sem visão do alvo por muito tempo (escondido sob a rocha): desiste deste rasante
                    beginRecover(t);
                }
            } else if (phase == DIVE) {
                Vec3 aim = t.position().add(0, t.getBbHeight() * 0.6, 0).subtract(position().add(0, getBbHeight() * 0.5, 0));
                Vec3 dir = aim.normalize();
                // Mergulho rápido (~1,2 bloco/tick), mas dá para reagir ao som do rasante
                setDeltaMovement(getDeltaMovement().scale(0.4).add(dir.scale(0.7)));
                float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
                setYRot(yaw);
                yBodyRot = yaw;
                getMoveControl().setWantedPosition(t.getX(), t.getY() + 1.0, t.getZ(), 2.0);
                if (getBoundingBox().inflate(0.35).intersects(t.getBoundingBox())) {
                    if (doHurtTarget(t)) t.knockback(0.6, getX() - t.getX(), getZ() - t.getZ());
                    beginRecover(t);
                } else if (timer > 40 || horizontalCollision || onGround()) {
                    beginRecover(t);
                }
            } else {
                getMoveControl().setWantedPosition(waypoint.x, waypoint.y, waypoint.z, 1.3);
            }
        }

        private void beginRecover(LivingEntity t) {
            phase = RECOVER;
            timer = 0;
            setDiving(false);
            Vec3 away = position().subtract(t.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 0.01 ? getLookAngle().multiply(-1, 0, -1) : away.normalize();
            waypoint = position().add(away.scale(7.0)).add(0, 5.0, 0);
        }
    }

    /** Sem rasante disponível: fica circulando o alvo do alto, como ave de rapina. */
    class CircleTargetGoal extends Goal {
        private float angle;
        private float dir = 1.0F;

        CircleTargetGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return getTarget() != null && getTarget().isAlive();
        }

        @Override
        public void start() {
            LivingEntity t = getTarget();
            if (t != null) angle = (float) Mth.atan2(getZ() - t.getZ(), getX() - t.getX());
            dir = random.nextBoolean() ? 1.0F : -1.0F;
        }

        @Override
        public void tick() {
            LivingEntity t = getTarget();
            if (t == null) return;
            angle += 0.06F * dir;
            if (horizontalCollision) dir = -dir;
            double r = 7.0;
            double y = t.getY() + 4.5 + Math.sin(tickCount * 0.05) * 1.0;
            getMoveControl().setWantedPosition(t.getX() + Math.cos(angle) * r, y, t.getZ() + Math.sin(angle) * r, 1.0);
        }
    }
}
