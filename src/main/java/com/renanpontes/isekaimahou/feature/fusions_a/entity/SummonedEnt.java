package com.renanpontes.isekaimahou.feature.fusions_a.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.fusions_a.FaRegistries;
import com.renanpontes.isekaimahou.feature.fusions_a.FaUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
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
 * Ent invocado (Invocar Ent): guardião-árvore lento e muito resistente. Esmaga com os braços, de vez em quando
 * pisa no chão e prende os inimigos em raízes, regenera sob o sol e teme o fogo. Um por dono; dura um tempo
 * limitado e volta para a terra no fim.
 */
public class SummonedEnt extends PathfinderMob implements OwnableEntity, GeoEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.summoned_ent.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.summoned_ent.walk");
    private static final RawAnimation SUMMON = RawAnimation.begin().thenPlay("animation.summoned_ent.summon");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.summoned_ent.attack");
    private static final RawAnimation STOMP = RawAnimation.begin().thenPlay("animation.summoned_ent.stomp");
    private static final ResourceLocation POWER_ID = IsekaiMahou.id("summoned_ent_power");
    private static final EntityDataAccessor<Boolean> DATA_EMERGING = SynchedEntityData.defineId(SummonedEnt.class, EntityDataSerializers.BOOLEAN);
    private static final int EMERGE_TICKS = 30;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private int life = 900;
    private float power = 1.0F;
    private int orphanTicks;
    private int emerge = EMERGE_TICKS;
    private int stompCooldown = 60;
    private int stompIn = -1;

    public SummonedEnt(EntityType<? extends SummonedEnt> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 70.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    // =====================================================================================
    //  Invocação
    // =====================================================================================

    /** Invoca (ou renova) o Ent do conjurador perto de {@code near}. */
    @Nullable
    public static SummonedEnt summon(SpellContext ctx, Vec3 near, int life, float power) {
        ServerLevel level = ctx.level();
        LivingEntity owner = ctx.caster();
        if (owner == null) return null;
        UUID id = owner.getUUID();
        List<SummonedEnt> mine = new ArrayList<>(level.getEntities(FaRegistries.SUMMONED_ENT.get(),
                e -> e.isAlive() && id.equals(e.ownerId)));
        if (!mine.isEmpty()) {
            SummonedEnt keep = mine.get(0);
            for (int i = 1; i < mine.size(); i++) mine.get(i).vanish();
            keep.renew(near, life);
            return keep;
        }
        SummonedEnt ent = FaRegistries.SUMMONED_ENT.get().create(level);
        if (ent == null) return null;
        Vec3 pos = ent.findSpot(near);
        ent.moveTo(pos.x, pos.y, pos.z, owner.getYRot(), 0);
        ent.setYHeadRot(owner.getYRot());
        ent.setup(owner, life, power);
        level.addFreshEntity(ent);
        ent.emergeFx(level);
        return ent;
    }

    /** Nascido de um ovo (criativo/testes): fica ligado ao jogador mais próximo, que é quem usou o ovo. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        if (spawnType == MobSpawnType.SPAWN_EGG && ownerId == null) {
            Player p = level.getNearestPlayer(this, 8.0);
            if (p != null) setup(p, life, power);
            emergeFx(level.getLevel());
        }
        return out;
    }

    private void renew(Vec3 near, int newLife) {
        life = Math.max(life, newLife);
        setHealth(getMaxHealth());
        if (!(level() instanceof ServerLevel sl)) return;
        if (distanceToSqr(near) > 10 * 10) relocate(sl, near);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.5, getZ(), 20, 0.6, 1.0, 0.6, 0);
        FaUtil.sound(sl, position(), SoundEvents.AZALEA_LEAVES_PLACE, 1.0F, 0.6F);
    }

    /** Afunda onde está e brota de novo perto de {@code near} (com a animação de sair do chão). */
    private void relocate(ServerLevel sl, Vec3 near) {
        sinkFx(sl);
        Vec3 p = findSpot(near);
        moveTo(p.x, p.y, p.z, getYRot(), getXRot());
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        emerge = EMERGE_TICKS;
        entityData.set(DATA_EMERGING, true);
        emergeFx(sl);
    }

    /** Enquanto sai do chão, o Ent não anda nem ataca (a animação de invocação está tocando). */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || entityData.get(DATA_EMERGING);
    }

    private Vec3 findSpot(Vec3 near) {
        for (int i = 0; i < 16; i++) {
            double ox = i == 0 ? 0 : (random.nextDouble() - 0.5) * 5, oz = i == 0 ? 0 : (random.nextDouble() - 0.5) * 5;
            Vec3 p = FaUtil.ground(level(), new Vec3(near.x + ox, near.y + 0.5, near.z + oz), 5);
            if (level().noCollision(this, getType().getSpawnAABB(p.x, p.y, p.z))) return p;
        }
        return near;
    }

    private void setup(LivingEntity owner, int life, float power) {
        this.ownerId = owner.getUUID();
        this.life = life;
        this.power = power;
        if (power > 1.01F) {
            scale(getAttribute(Attributes.MAX_HEALTH), power);
            scale(getAttribute(Attributes.ATTACK_DAMAGE), power);
        }
        setHealth(getMaxHealth());
    }

    private static void scale(@Nullable AttributeInstance a, float power) {
        if (a == null || a.getModifier(POWER_ID) != null) return;
        a.addPermanentModifier(new AttributeModifier(POWER_ID, power - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    // =====================================================================================
    //  Dono e aliados
    // =====================================================================================

    @Override
    @Nullable
    public UUID getOwnerUUID() { return ownerId; }

    @Override
    @Nullable
    public LivingEntity getOwner() {
        if (ownerId == null) return null;
        Player p = level().getPlayerByUUID(ownerId);
        if (p != null) return p;
        if (level() instanceof ServerLevel sl && sl.getEntity(ownerId) instanceof LivingEntity le) return le;
        return null;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other == null) return false;
        if (ownerId != null) {
            if (ownerId.equals(other.getUUID())) return true;
            if (other instanceof OwnableEntity o && ownerId.equals(o.getOwnerUUID())) return true;
            LivingEntity owner = getOwner();
            if (owner != null && owner != other) {
                if (owner.isAlliedTo(other)) return true;
                if (owner instanceof Player && other instanceof Player && level().getServer() != null
                        && !level().getServer().isPvpAllowed()) return true;
            }
        }
        return super.isAlliedTo(other);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isAlliedTo(target) && super.canAttack(target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity src = source.getEntity();
        if (src != null && isAlliedTo(src) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (source.is(DamageTypeTags.IS_FIRE)) amount *= 1.5F; // madeira queima
        return super.hurt(source, amount);
    }

    // =====================================================================================
    //  IA
    // =====================================================================================

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
        goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.0, 10.0F, 3.5F));
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new DefendOwnerGoal(this));
        targetSelector.addGoal(2, new AssistOwnerGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                e -> e instanceof Enemy && !isAlliedTo(e)));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        triggerAnim("action", "attack");
        if (hit && level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()),
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 12, 0.3, 0.3, 0.3, 0.1);
            FaUtil.sound(sl, target.position(), SoundEvents.WOOD_BREAK, 1.0F, 0.6F);
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (emerge > 0 && --emerge == 0) entityData.set(DATA_EMERGING, false);
        if (--life <= 0) {
            vanish();
            return;
        }
        if (tickCount % 20 == 0) {
            LivingEntity owner = getOwner();
            if (ownerId != null && (owner == null || !owner.isAlive())) {
                orphanTicks += 20;
                if (orphanTicks >= 200) {
                    vanish();
                    return;
                }
            } else {
                orphanTicks = 0;
            }
        }
        // Regenera sob o sol
        if (tickCount % 40 == 0 && sl.isDay() && sl.canSeeSky(blockPosition().above(2)) && getHealth() < getMaxHealth()) {
            heal(2.0F);
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 2.6, getZ(), 4, 0.6, 0.3, 0.6, 0);
        }
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || isAlliedTo(t))) {
            setTarget(null);
            t = null;
        }
        // Pisão de raízes: prende quem está perto
        if (stompCooldown > 0) stompCooldown--;
        if (stompIn > 0 && --stompIn == 0) stomp(sl);
        if (t != null && stompCooldown <= 0 && stompIn < 0 && distanceToSqr(t) < 4.5 * 4.5 && onGround()) {
            triggerAnim("action", "stomp");
            stompIn = 12;
            stompCooldown = 140;
        }
    }

    private void stomp(ServerLevel sl) {
        stompIn = -1;
        Vec3 c = position();
        LivingEntity owner = getOwner();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.8, 1.0, 3.8),
                e -> e != this && e.isAlive() && !isAlliedTo(e) && (e instanceof Enemy || e == getTarget()))) {
            e.hurt(FaUtil.summonSource(this, owner), 5.0F * power);
            e.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, 50, 0), this);
            FaUtil.spiral(sl, e, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState()), 2, 16);
        }
        sl.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, Blocks.ROOTED_DIRT.defaultBlockState()),
                c.x, c.y + 0.1, c.z, 40, 1.6, 0.1, 1.6, 0.2);
        FaUtil.shockwave(sl, c.add(0, 0.2, 0), FaUtil.dust(0x5B8A2E, 1.4F), 36, 0.35);
        FaUtil.sound(sl, c, SoundEvents.ROOTED_DIRT_PLACE, 1.4F, 0.6F);
        FaUtil.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 0.4F, 1.6F);
    }

    private void clientFx() {
        if (entityData.get(DATA_EMERGING)) {
            if (random.nextInt(2) == 0) {
                level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                        getX() + (random.nextDouble() - 0.5) * 1.6, getY() + 0.1, getZ() + (random.nextDouble() - 0.5) * 1.6,
                        0, 0.2, 0);
            }
            return;
        }
        if (FaUtil.reducedParticles() || random.nextInt(14) != 0) return;
        level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()),
                getX() + (random.nextDouble() - 0.5) * 1.6, getY() + 2.7, getZ() + (random.nextDouble() - 0.5) * 1.6, 0, 0, 0);
    }

    // =====================================================================================
    //  Entrada/saída de cena
    // =====================================================================================

    private void emergeFx(ServerLevel sl) {
        Vec3 c = position();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, Blocks.ROOTED_DIRT.defaultBlockState()),
                c.x, c.y + 0.1, c.z, 50, 1.0, 0.1, 1.0, 0.25);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 1.5, c.z, 25, 0.8, 1.2, 0.8, 0);
        FaUtil.sound(sl, c, SoundEvents.ROOTED_DIRT_BREAK, 1.5F, 0.6F);
        FaUtil.sound(sl, c, SoundEvents.AZALEA_LEAVES_PLACE, 1.2F, 0.5F);
    }

    private void sinkFx(ServerLevel sl) {
        Vec3 c = position();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState()),
                c.x, c.y + 2.0, c.z, 30, 0.7, 0.8, 0.7, 0.1);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, Blocks.DIRT.defaultBlockState()),
                c.x, c.y + 0.1, c.z, 30, 0.8, 0.1, 0.8, 0.2);
        FaUtil.sound(sl, c, SoundEvents.ROOTED_DIRT_BREAK, 1.2F, 0.5F);
    }

    /** Volta para a terra (fim do tempo, dono foi embora ou nova invocação). */
    public void vanish() {
        if (level() instanceof ServerLevel sl) sinkFx(sl);
        discard();
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl) sinkFx(sl);
        super.die(source);
    }

    // =====================================================================================
    //  Sons
    // =====================================================================================

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.AZALEA_LEAVES_STEP; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.WOOD_HIT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.WOOD_BREAK; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.WOOD_STEP, 0.6F, 0.5F);
    }

    @Override
    public float getVoicePitch() { return 0.5F; }

    // =====================================================================================
    //  Regras de invocação
    // =====================================================================================

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }

    @Override
    public boolean requiresCustomPersistence() { return true; }

    @Override
    protected boolean shouldDropLoot() { return false; }

    @Override
    public boolean canBeLeashed() { return false; }

    @Override
    protected boolean shouldDespawnInPeaceful() { return false; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_EMERGING, true);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerId != null) tag.putUUID("SummonOwner", ownerId);
        tag.putInt("SummonLife", life);
        tag.putFloat("SummonPower", power);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ownerId = tag.hasUUID("SummonOwner") ? tag.getUUID("SummonOwner") : null;
        life = tag.contains("SummonLife") ? tag.getInt("SummonLife") : 900;
        power = tag.contains("SummonPower") ? tag.getFloat("SummonPower") : 1.0F;
        emerge = 0;
        entityData.set(DATA_EMERGING, false);
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            if (entityData.get(DATA_EMERGING)) return state.setAndContinue(SUMMON);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("stomp", STOMP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // =====================================================================================
    //  Objetivos de IA
    // =====================================================================================

    /** Segue o dono; se ficar muito para trás, brota do chão perto dele. */
    static class FollowOwnerGoal extends Goal {
        private final SummonedEnt mob;
        private final double speed;
        private final float startDist;
        private final float stopDist;
        private int recalc;

        FollowOwnerGoal(SummonedEnt mob, double speed, float startDist, float stopDist) {
            this.mob = mob;
            this.speed = speed;
            this.startDist = startDist;
            this.stopDist = stopDist;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity o = mob.getOwner();
            return o != null && !o.isSpectator() && mob.getTarget() == null && mob.distanceToSqr(o) > startDist * startDist;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity o = mob.getOwner();
            return o != null && mob.getTarget() == null && !mob.getNavigation().isDone()
                    && mob.distanceToSqr(o) > stopDist * stopDist;
        }

        @Override
        public void start() { recalc = 0; }

        @Override
        public void stop() { mob.getNavigation().stop(); }

        @Override
        public void tick() {
            LivingEntity o = mob.getOwner();
            if (o == null) return;
            mob.getLookControl().setLookAt(o, 10.0F, mob.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = adjustedTickDelay(10);
            if (mob.distanceToSqr(o) > 22 * 22 && mob.level() instanceof ServerLevel sl) {
                mob.relocate(sl, o.position().add(o.getLookAngle().multiply(-2.5, 0, -2.5)));
            } else {
                mob.getNavigation().moveTo(o, speed);
            }
        }
    }

    /** Ataca quem machucou o dono. */
    static class DefendOwnerGoal extends TargetGoal {
        private final SummonedEnt ent;
        private LivingEntity attacker;
        private int timestamp;

        DefendOwnerGoal(SummonedEnt ent) {
            super(ent, false);
            this.ent = ent;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = ent.getOwner();
            if (owner == null) return false;
            attacker = owner.getLastHurtByMob();
            return owner.getLastHurtByMobTimestamp() != timestamp && attacker != null && !ent.isAlliedTo(attacker)
                    && canAttack(attacker, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(attacker);
            LivingEntity owner = ent.getOwner();
            if (owner != null) timestamp = owner.getLastHurtByMobTimestamp();
            super.start();
        }
    }

    /** Ataca o mesmo alvo do dono. */
    static class AssistOwnerGoal extends TargetGoal {
        private final SummonedEnt ent;
        private LivingEntity victim;
        private int timestamp;

        AssistOwnerGoal(SummonedEnt ent) {
            super(ent, false);
            this.ent = ent;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = ent.getOwner();
            if (owner == null) return false;
            if (owner instanceof Mob m && m.getTarget() != null && !ent.isAlliedTo(m.getTarget())) {
                victim = m.getTarget();
                return victim != mob.getTarget() && canAttack(victim, TargetingConditions.DEFAULT);
            }
            victim = owner.getLastHurtMob();
            return owner.getLastHurtMobTimestamp() != timestamp && victim != null && !ent.isAlliedTo(victim)
                    && canAttack(victim, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(victim);
            LivingEntity owner = ent.getOwner();
            if (owner != null) timestamp = owner.getLastHurtMobTimestamp();
            super.start();
        }
    }
}
