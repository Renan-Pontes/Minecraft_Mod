package com.renanpontes.isekaimahou.feature.fire_water.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base das invocações elementais (Salamandra, Ondina): têm dono, duram um tempo limitado,
 * defendem e ajudam o dono, nunca atacam aliados e somem com partículas quando o tempo acaba.
 */
public abstract class FwSummon extends PathfinderMob implements OwnableEntity, GeoEntity {
    private static final ResourceLocation POWER_ID = IsekaiMahou.id("fire_water_summon_power");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    private int life = 600;
    private float power = 1.0F;
    private int orphanTicks;

    protected FwSummon(EntityType<? extends FwSummon> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    // =====================================================================================
    //  Invocação
    // =====================================================================================

    /**
     * Invoca perto de {@code near}, num lugar livre, respeitando o limite de invocações do mesmo tipo por dono.
     * No limite, a invocação mais antiga é RENOVADA (vida cheia, tempo novo, vem para perto) em vez de sumir e
     * nascer outra — assim zonas e Domínios que repetem o efeito não ficam trocando de criatura sem parar.
     * Retorna null se não houver conjurador.
     */
    @Nullable
    public static <T extends FwSummon> T summon(SpellContext ctx, EntityType<T> type, Vec3 near, int life, float power,
                                               int maxPerOwner) {
        ServerLevel level = ctx.level();
        LivingEntity owner = ctx.caster();
        if (owner == null) return null;
        UUID id = owner.getUUID();
        List<T> mine = new ArrayList<>(level.getEntities(type, e -> e.isAlive() && id.equals(e.getOwnerUUID())));
        while (mine.size() > maxPerOwner) {
            T oldest = oldest(mine);
            oldest.vanish();
            mine.remove(oldest);
        }
        if (mine.size() >= maxPerOwner) {
            T renewed = oldest(mine);
            renewed.renew(near, life);
            return renewed;
        }
        T mob = type.create(level);
        if (mob == null) return null;
        Vec3 pos = mob.findSpot(near);
        mob.moveTo(pos.x, pos.y, pos.z, owner.getYRot(), 0);
        mob.setYHeadRot(owner.getYRot());
        mob.setup(owner, life, power);
        level.addFreshEntity(mob);
        mob.spawnFx((ServerLevel) mob.level());
        return mob;
    }

    /**
     * Nascida de um ovo (criativo/testes): fica ligada ao jogador mais próximo, que é quem usou o ovo,
     * com a duração padrão de 30 s — igual a uma invocação feita pelo glifo.
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, spawnType, data);
        if (spawnType == MobSpawnType.SPAWN_EGG && ownerId == null) {
            Player p = level.getNearestPlayer(this, 8.0);
            if (p != null) setup(p, life, power);
            spawnFx(level.getLevel());
        }
        return out;
    }

    private static <T extends FwSummon> T oldest(List<T> list) {
        T oldest = list.get(0);
        for (T e : list) if (e.lifeLeft() < oldest.lifeLeft()) oldest = e;
        return oldest;
    }

    /** Renova a invocação: tempo novo, vida cheia e, se estiver longe, reaparece perto do ponto pedido. */
    protected void renew(Vec3 near, int newLife) {
        this.life = Math.max(this.life, newLife);
        setHealth(getMaxHealth());
        if (!(level() instanceof ServerLevel sl)) return;
        if (distanceToSqr(near) > 8 * 8) {
            vanishFx(sl);
            Vec3 p = findSpot(near);
            moveTo(p.x, p.y, p.z, getYRot(), getXRot());
            getNavigation().stop();
        }
        spawnFx(sl);
    }

    /** Procura um lugar livre perto (e com chão, para quem anda). */
    protected Vec3 findSpot(Vec3 near) {
        Level lv = level();
        for (int i = 0; i < 16; i++) {
            double ox = i == 0 ? 0 : (random.nextDouble() - 0.5) * 4, oz = i == 0 ? 0 : (random.nextDouble() - 0.5) * 4;
            Vec3 p = new Vec3(near.x + ox, near.y + 0.5, near.z + oz);
            if (!flies()) p = com.renanpontes.isekaimahou.feature.fire_water.FwUtil.ground(lv, p, 4);
            if (lv.noCollision(this, getType().getSpawnAABB(p.x, p.y, p.z))) return p;
        }
        return near;
    }

    /** Configura dono, duração (ticks) e poder (escala vida e dano). */
    public void setup(LivingEntity owner, int life, float power) {
        this.ownerId = owner != null ? owner.getUUID() : null;
        this.life = life;
        this.power = power;
        if (power > 1.01F) {
            scale(getAttribute(Attributes.MAX_HEALTH), power);
            scale(getAttribute(Attributes.ATTACK_DAMAGE), power);
        }
        setHealth(getMaxHealth());
    }

    private static void scale(AttributeInstance a, float power) {
        if (a == null || a.getModifier(POWER_ID) != null) return;
        a.addPermanentModifier(new AttributeModifier(POWER_ID, power - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    public float power() { return power; }

    /** Ticks que faltam para a invocação sumir. */
    public int lifeLeft() { return life; }

    /** Voa (Ondina) ou anda (Salamandra)? */
    protected boolean flies() { return false; }

    protected abstract void spawnFx(ServerLevel level);

    protected abstract void vanishFx(ServerLevel level);

    /** Some (fim do tempo, dono foi embora ou limite de invocações). */
    public void vanish() {
        if (level() instanceof ServerLevel sl) {
            vanishFx(sl);
            sl.sendParticles(Vfx.poof(), getX(), getY() + getBbHeight() * 0.5, getZ(), 12, 0.3, 0.3, 0.3, 0.03);
        }
        discard();
    }

    // =====================================================================================
    //  Dono e aliados
    // =====================================================================================

    @Override
    @Nullable
    public UUID getOwnerUUID() { return ownerId; }

    /** O dono pode ser um jogador ou um mob que conjurou (cultistas, chefes). */
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
            if (ownerId != null && (owner == null || !owner.isAlive())) {
                orphanTicks += 20;
                if (orphanTicks >= 200) vanish(); // dono saiu do mundo ou morreu
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
    protected boolean teleportNear(LivingEntity owner) {
        for (int i = 0; i < 12; i++) {
            int dx = random.nextIntBetweenInclusive(-3, 3), dz = random.nextIntBetweenInclusive(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            BlockPos base = owner.blockPosition().offset(dx, flies() ? 1 : 0, dz);
            Vec3 p = flies() ? Vec3.atBottomCenterOf(base)
                    : com.renanpontes.isekaimahou.feature.fire_water.FwUtil.ground(level(), Vec3.atBottomCenterOf(base).add(0, 1, 0), 3);
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
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

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
        life = tag.contains("SummonLife") ? tag.getInt("SummonLife") : 600;
        power = tag.contains("SummonPower") ? tag.getFloat("SummonPower") : 1.0F;
    }

    // =====================================================================================
    //  Objetivos de IA compartilhados
    // =====================================================================================

    /** Segue o dono; se ficar muito longe, teleporta. */
    public static class FollowOwnerGoal extends Goal {
        private final FwSummon mob;
        private final double speed;
        private final float startDist;
        private final float stopDist;
        private int recalc;

        public FollowOwnerGoal(FwSummon mob, double speed, float startDist, float stopDist) {
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
            if (mob.distanceToSqr(o) > 20 * 20) mob.teleportNear(o);
            else mob.getNavigation().moveTo(o, speed);
        }
    }

    /** Ataca quem machucou o dono. */
    public static class DefendOwnerGoal extends TargetGoal {
        private final FwSummon summon;
        private LivingEntity attacker;
        private int timestamp;

        public DefendOwnerGoal(FwSummon summon) {
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
        private final FwSummon summon;
        private LivingEntity victim;
        private int timestamp;

        public AssistOwnerGoal(FwSummon summon) {
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
