package com.renanpontes.isekaimahou.feature.fusions_b.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Base das invocações das fusões B (Diabrete Infernal, Clones do Crepúsculo): têm dono, duram um tempo limitado,
 * defendem e ajudam o dono, nunca atacam aliados e somem com partículas quando o tempo acaba ou o dono some.
 */
public abstract class FbSummon extends PathfinderMob implements OwnableEntity {
    private static final ResourceLocation POWER_ID = IsekaiMahou.id("fusions_b_summon_power");
    /** Dono sincronizado (o clone usa para desenhar a pele do dono). */
    protected static final EntityDataAccessor<java.util.Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(FbSummon.class, EntityDataSerializers.OPTIONAL_UUID);

    private int life = 400;
    private float power = 1.0F;
    private int orphanTicks;

    protected FbSummon(EntityType<? extends FbSummon> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(OWNER_UUID, java.util.Optional.empty());
    }

    // =====================================================================================
    //  Invocação
    // =====================================================================================

    /**
     * Garante no máximo {@code max} invocações que satisfazem {@code same} por dono: as mais antigas somem.
     */
    public static <T extends FbSummon> void limit(ServerLevel level, EntityType<T> type, UUID owner, int max, Predicate<T> same) {
        List<T> mine = new ArrayList<>(level.getEntities(type, e -> e.isAlive() && owner.equals(e.getOwnerUUID()) && same.test(e)));
        while (mine.size() > max) {
            T oldest = mine.get(0);
            for (T e : mine) if (e.lifeLeft() < oldest.lifeLeft()) oldest = e;
            oldest.vanish();
            mine.remove(oldest);
        }
    }

    /** Configura dono, duração (ticks) e poder (escala vida e dano). */
    public void setup(@Nullable LivingEntity owner, int life, float power) {
        entityData.set(OWNER_UUID, java.util.Optional.ofNullable(owner != null ? owner.getUUID() : null));
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

    public float power() { return power; }

    public int lifeLeft() { return life; }

    /** Renova o tempo e a vida (quando o dono conjura de novo e já está no limite). */
    public void renew(int newLife) {
        life = Math.max(life, newLife);
        setHealth(getMaxHealth());
        if (level() instanceof ServerLevel sl) spawnFx(sl);
    }

    /** Procura um lugar livre perto (com chão, para quem anda). */
    protected Vec3 findSpot(Vec3 near, boolean flies) {
        Level lv = level();
        for (int i = 0; i < 16; i++) {
            double ox = i == 0 ? 0 : (random.nextDouble() - 0.5) * 4, oz = i == 0 ? 0 : (random.nextDouble() - 0.5) * 4;
            Vec3 p = new Vec3(near.x + ox, near.y + 0.5, near.z + oz);
            if (!flies) p = FbUtil.ground(lv, p, 4);
            if (lv.noCollision(this, getType().getSpawnAABB(p.x, p.y, p.z))) return p;
        }
        return near;
    }

    protected abstract void spawnFx(ServerLevel level);

    protected abstract void vanishFx(ServerLevel level);

    /** Some (fim do tempo, dono foi embora ou limite de invocações). */
    public void vanish() {
        if (level() instanceof ServerLevel sl) {
            vanishFx(sl);
            sl.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() * 0.5, getZ(), 10, 0.3, 0.3, 0.3, 0.03);
        }
        discard();
    }

    // =====================================================================================
    //  Dono e aliados
    // =====================================================================================

    @Override
    @Nullable
    public UUID getOwnerUUID() { return entityData.get(OWNER_UUID).orElse(null); }

    @Override
    @Nullable
    public LivingEntity getOwner() {
        UUID id = getOwnerUUID();
        if (id == null) return null;
        Player p = level().getPlayerByUUID(id);
        if (p != null) return p;
        if (level() instanceof ServerLevel sl && sl.getEntity(id) instanceof LivingEntity le) return le;
        return null;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other == null) return false;
        UUID id = getOwnerUUID();
        if (id != null) {
            if (id.equals(other.getUUID())) return true;
            if (other instanceof OwnableEntity o && id.equals(o.getOwnerUUID())) return true;
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
        // Fogo amigo: o dono e os aliados não machucam a invocação
        if (src != null && isAlliedTo(src) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        if (--life <= 0) {
            vanish();
            return;
        }
        if (tickCount % 20 == 0) {
            LivingEntity owner = getOwner();
            if (getOwnerUUID() != null && (owner == null || !owner.isAlive())) {
                orphanTicks += 20;
                if (orphanTicks >= 100) vanish(); // dono saiu do mundo ou morreu
            } else {
                orphanTicks = 0;
            }
        }
        LivingEntity t = getTarget();
        if (t != null && (!t.isAlive() || isAlliedTo(t))) setTarget(null);
    }

    /** Partículas ambientes (só cliente). */
    protected abstract void clientFx();

    /** Alvos padrão: defende o dono, ajuda o dono, revida e caça monstros por perto. */
    protected void addTargetGoals() {
        targetSelector.addGoal(1, new DefendOwnerGoal(this));
        targetSelector.addGoal(2, new AssistOwnerGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Mob.class, 10, true, false,
                e -> e instanceof Enemy && !isAlliedTo(e)));
    }

    /** Teleporta para perto do dono (quando ficou muito para trás). */
    protected boolean teleportNear(LivingEntity owner, boolean flies) {
        for (int i = 0; i < 12; i++) {
            int dx = random.nextIntBetweenInclusive(-3, 3), dz = random.nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            BlockPos base = owner.blockPosition().offset(dx, flies ? 1 : 0, dz);
            Vec3 p = flies ? Vec3.atBottomCenterOf(base) : FbUtil.ground(level(), Vec3.atBottomCenterOf(base).add(0, 1, 0), 3);
            if (level().noCollision(this, getType().getSpawnAABB(p.x, p.y, p.z))) {
                if (level() instanceof ServerLevel sl) vanishFx(sl);
                moveTo(p.x, p.y, p.z, getYRot(), getXRot());
                getNavigation().stop();
                if (level() instanceof ServerLevel sl) spawnFx(sl);
                return true;
            }
        }
        return false;
    }

    // =====================================================================================
    //  Regras de mob temporário
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
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID id = getOwnerUUID();
        if (id != null) tag.putUUID("SummonOwner", id);
        tag.putInt("SummonLife", life);
        tag.putFloat("SummonPower", power);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(OWNER_UUID, java.util.Optional.ofNullable(tag.hasUUID("SummonOwner") ? tag.getUUID("SummonOwner") : null));
        life = tag.contains("SummonLife") ? tag.getInt("SummonLife") : 400;
        power = tag.contains("SummonPower") ? tag.getFloat("SummonPower") : 1.0F;
    }

    // =====================================================================================
    //  Objetivos de alvo compartilhados
    // =====================================================================================

    /** Ataca quem machucou o dono. */
    public static class DefendOwnerGoal extends TargetGoal {
        private final FbSummon summon;
        private LivingEntity attacker;
        private int timestamp;

        public DefendOwnerGoal(FbSummon summon) {
            super(summon, false);
            this.summon = summon;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = summon.getOwner();
            if (owner == null) return false;
            attacker = owner.getLastHurtByMob();
            return owner.getLastHurtByMobTimestamp() != timestamp && attacker != null && !summon.isAlliedTo(attacker)
                    && canAttack(attacker, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(attacker);
            LivingEntity owner = summon.getOwner();
            if (owner != null) timestamp = owner.getLastHurtByMobTimestamp();
            super.start();
        }
    }

    /** Ataca o mesmo alvo do dono (quem o dono acertou, ou o alvo do mob conjurador). */
    public static class AssistOwnerGoal extends TargetGoal {
        private final FbSummon summon;
        private LivingEntity victim;
        private int timestamp;

        public AssistOwnerGoal(FbSummon summon) {
            super(summon, false);
            this.summon = summon;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = summon.getOwner();
            if (owner == null) return false;
            if (owner instanceof Mob m && m.getTarget() != null && !summon.isAlliedTo(m.getTarget())) {
                victim = m.getTarget();
                return victim != mob.getTarget() && canAttack(victim, TargetingConditions.DEFAULT);
            }
            victim = owner.getLastHurtMob();
            return owner.getLastHurtMobTimestamp() != timestamp && victim != null && !summon.isAlliedTo(victim)
                    && canAttack(victim, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(victim);
            LivingEntity owner = summon.getOwner();
            if (owner != null) timestamp = owner.getLastHurtMobTimestamp();
            super.start();
        }
    }
}
