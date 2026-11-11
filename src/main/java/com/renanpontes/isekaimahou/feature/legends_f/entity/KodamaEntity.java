package com.renanpontes.isekaimahou.feature.legends_f.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Kodama (homenagem aos espíritos da floresta de Princesa Mononoke): espiritozinhos brancos que seguem o Druida,
 * balançam a cabeça fazendo "toc-toc" e a cada 3 s curam o aliado mais ferido por perto (o dobro perto de árvores).
 * Quando alguém fere o dono, os Kodama perto prendem o agressor em raízes. Inofensivos, frágeis e temporários.
 */
public class KodamaEntity extends PathfinderMob implements OwnableEntity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(KodamaEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_RATTLE = SynchedEntityData.defineId(KodamaEntity.class, EntityDataSerializers.INT);

    @Nullable
    private UUID owner;
    private int lifetime = 1800;
    private float heal = 1.5F;
    private int nextHeal = 40;
    private long lastDefend;

    public KodamaEntity(EntityType<? extends KodamaEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    public void setup(LivingEntity owner, int lifetime, float healBase) {
        this.owner = owner.getUUID();
        entityData.set(DATA_OWNER, Optional.of(this.owner));
        this.lifetime = lifetime;
        this.heal = healBase;
        this.nextHeal = 20 + random.nextInt(40);
        setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_RATTLE, 0);
    }

    /** Ticks restantes do "toc-toc" (cabeça balançando), visto pelo cliente. */
    public int rattle() {
        return entityData.get(DATA_RATTLE);
    }

    @Override
    @Nullable
    public UUID getOwnerUUID() {
        return level().isClientSide ? entityData.get(DATA_OWNER).orElse(null) : owner;
    }

    @Override
    @Nullable
    public LivingEntity getOwner() {
        UUID id = getOwnerUUID();
        return id == null ? null : level().getPlayerByUUID(id);
    }

    @Override
    public boolean isAlliedTo(Entity e) {
        UUID id = getOwnerUUID();
        if (id != null && (id.equals(e.getUUID()) || (e instanceof OwnableEntity oe && id.equals(oe.getOwnerUUID())))) return true;
        return super.isAlliedTo(e);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new FollowGoal());
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(8) == 0) {
                level().addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + 0.5, getZ(), 0, 0.01, 0);
            }
            return;
        }
        int r = entityData.get(DATA_RATTLE);
        if (r > 0) entityData.set(DATA_RATTLE, r - 1);
        ServerLevel sl = (ServerLevel) level();
        LivingEntity o = getOwner();
        if (owner == null || --lifetime <= 0 || (o == null && tickCount > 40)) {
            poof(sl);
            return;
        }
        if (o == null) return;
        if (distanceToSqr(o) > 24 * 24 && o.onGround()) teleportTo(o.getX() + random.nextGaussian(), o.getY(), o.getZ() + random.nextGaussian());
        if (random.nextInt(140) == 0) rattle(sl);
        if (--nextHeal <= 0) {
            nextHeal = 60;
            healTick(sl, o);
        }
        // Alguém feriu o dono agora há pouco: raízes no agressor
        LivingEntity attacker = o.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && o.tickCount - o.getLastHurtByMobTimestamp() < 10
                && sl.getGameTime() - lastDefend > 60 && attacker.distanceToSqr(this) < 10 * 10 && !isAlliedTo(attacker)) {
            lastDefend = sl.getGameTime();
            Statuses.apply(attacker, Statuses.Kind.ROOTED, 1, 30, o, 1F);
            sl.sendParticles(ParticleTypes.COMPOSTER, attacker.getX(), attacker.getY() + 0.2, attacker.getZ(), 12, 0.4, 0.1, 0.4, 0.02);
            rattle(sl);
        }
    }

    private void healTick(ServerLevel sl, LivingEntity o) {
        List<LivingEntity> near = sl.getEntitiesOfClass(LivingEntity.class, new AABB(blockPosition()).inflate(8),
                e -> e.isAlive() && (e == o || isAlliedTo(e) || (e instanceof Player && o instanceof Player && !sl.getServer().isPvpAllowed()))
                        && e.getHealth() < e.getMaxHealth());
        LivingEntity worst = null;
        float frac = 1F;
        for (LivingEntity e : near) {
            float f = e.getHealth() / Math.max(1F, e.getMaxHealth());
            if (f < frac) {
                frac = f;
                worst = e;
            }
        }
        if (worst == null) return;
        float amount = heal * Progression.HEAL_SCALE * (nearTrees(sl) ? 2F : 1F);
        worst.heal(amount);
        worst.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, true), o);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, worst.getX(), worst.getY() + worst.getBbHeight() * 0.6, worst.getZ(), 6, 0.3, 0.3, 0.3, 0);
        sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + 0.6, getZ(), 4, 0.2, 0.2, 0.2, 0);
        rattle(sl);
    }

    private boolean nearTrees(ServerLevel sl) {
        BlockPos c = blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, -1, -3), c.offset(3, 4, 3))) {
            if (sl.getBlockState(p).is(BlockTags.LOGS)) return true;
        }
        return false;
    }

    private void rattle(ServerLevel sl) {
        entityData.set(DATA_RATTLE, 24);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BAMBOO_WOOD_HIT, SoundSource.NEUTRAL, 0.6F, 1.6F + random.nextFloat() * 0.3F);
    }

    private void poof(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + 0.4, getZ(), 12, 0.25, 0.3, 0.25, 0.02);
        sl.sendParticles(ParticleTypes.WHITE_ASH, getX(), getY() + 0.4, getZ(), 10, 0.25, 0.3, 0.25, 0.02);
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity src = source.getEntity();
        if (src != null && isAlliedTo(src)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel sl) poof(sl);
        else discard();
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public boolean canBeLeashed() { return false; }

    @Override
    public boolean shouldDropExperience() { return false; }

    @Override
    protected boolean shouldDropLoot() { return false; }

    @Override
    public boolean canUsePortal(boolean allowPassengers) { return false; }

    /** Segue o dono pulando atrás dele. */
    private class FollowGoal extends Goal {
        private LivingEntity o;
        private int recalc;

        FollowGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            o = getOwner();
            return o != null && distanceToSqr(o) > 4 * 4;
        }

        @Override
        public boolean canContinueToUse() {
            return o != null && o.isAlive() && distanceToSqr(o) > 2 * 2 && !getNavigation().isDone();
        }

        @Override
        public void start() {
            recalc = 0;
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(o, 10F, getMaxHeadXRot());
            if (--recalc <= 0) {
                recalc = adjustedTickDelay(10);
                getNavigation().moveTo(o, 1.2);
            }
        }

        @Override
        public void stop() {
            o = null;
            getNavigation().stop();
        }
    }
}
