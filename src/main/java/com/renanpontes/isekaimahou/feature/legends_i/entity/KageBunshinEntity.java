package com.renanpontes.isekaimahou.feature.legends_i.entity;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.legends_i.LiRegistry;
import com.renanpontes.isekaimahou.feature.legends_i.LiUtil;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Clone das Sombras (Kage Bunshin no Jutsu, homenagem a Naruto): uma cópia do conjurador — mesma pele e a mesma arma
 * na mão (só visual: a arma não soma ataque) — que luta pelo dono por pouco tempo e some numa nuvem de fumaça ao levar
 * o 2º golpe, ao acabar o tempo, se o dono sumir ou se afastar demais. É uma MANIFESTAÇÃO: não é salva no mundo.
 * Aliado do dono para as magias ({@link OwnableEntity}). Em Modo Paz ou em zona segura não ataca.
 */
public class KageBunshinEntity extends PathfinderMob implements OwnableEntity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(KageBunshinEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    public static final int MAX_PER_OWNER = 5;
    /** dono → clones vivos (para o limite e o "pof" do mais velho). */
    private static final Map<UUID, List<KageBunshinEntity>> BY_OWNER = new HashMap<>();

    @Nullable
    private UUID owner;
    private int life = 300;
    private int hits;
    @Nullable
    private LivingEntity focus;

    public KageBunshinEntity(EntityType<? extends KageBunshinEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_OWNER, Optional.empty());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        goalSelector.addGoal(5, new FollowOwnerGoal());
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerFoeGoal());
    }

    // =====================================================================================
    //  Criação
    // =====================================================================================

    /**
     * Cria {@code n} clones de {@code owner} em volta de {@code around}. Se {@code focus} não é nulo, todos vão atrás
     * dele. Devolve quantos surgiram.
     */
    public static int spawnAround(ServerLevel level, LivingEntity owner, Vec3 around, int n, int ticks, float power,
                                  @Nullable LivingEntity focus) {
        List<KageBunshinEntity> mine = BY_OWNER.computeIfAbsent(owner.getUUID(), k -> new ArrayList<>());
        mine.removeIf(c -> c.isRemoved() || !c.isAlive());
        int made = 0;
        double ownerAtk = owner.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? owner.getAttributeValue(Attributes.ATTACK_DAMAGE) : 3.0;
        for (int i = 0; i < n; i++) {
            while (mine.size() >= MAX_PER_OWNER) mine.remove(0).poof();
            KageBunshinEntity c = LiRegistry.KAGE_BUNSHIN.get().create(level);
            if (c == null) break;
            double a = Math.PI * 2 * i / n + level.random.nextDouble() * 0.6;
            double r = 1.6 + level.random.nextDouble() * 0.8;
            Vec3 p = around.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            c.moveTo(p.x, p.y, p.z, owner.getYRot(), 0F);
            if (!level.noCollision(c)) c.moveTo(around.x, around.y, around.z, owner.getYRot(), 0F);
            c.setup(owner, ticks, power, ownerAtk, focus);
            if (!level.addFreshEntity(c)) continue;
            mine.add(c);
            c.puff();
            made++;
        }
        // Isca: monstros que miravam no dono podem se confundir
        if (made > 0) {
            for (Mob m : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(16), m -> m.getTarget() == owner)) {
                if (level.random.nextBoolean()) m.setTarget(mine.get(level.random.nextInt(mine.size())));
            }
        }
        return made;
    }

    private void setup(LivingEntity owner, int ticks, float power, double ownerAtk, @Nullable LivingEntity focus) {
        this.owner = owner.getUUID();
        entityData.set(DATA_OWNER, Optional.of(this.owner));
        this.life = ticks;
        this.focus = focus;
        var hp = getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(Math.max(10.0, owner.getMaxHealth() * 0.25));
        setHealth(getMaxHealth());
        var atk = getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(Math.max(3.0, ownerAtk * 0.6 * Math.min(2.0F, power)));
        // A aparência: mesma arma e armadura, só para enfeite (sem atributos e sem cair no chão)
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR) continue;
            ItemStack s = owner.getItemBySlot(slot);
            if (s.isEmpty()) continue;
            ItemStack copy = s.copyWithCount(1);
            copy.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            setItemSlot(slot, copy);
            setDropChance(slot, 0F);
        }
        setCustomName(owner.getName());
        setCustomNameVisible(false);
    }

    // =====================================================================================
    //  Vida curta
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        LivingEntity o = ownerEntity();
        if (--life <= 0 || o == null || !o.isAlive() || o.distanceToSqr(this) > 48 * 48) {
            poof();
            return;
        }
        if (LiUtil.calm(o) && getTarget() != null) setTarget(null);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        Entity by = src.getEntity();
        if (by != null && owner != null && (by.getUUID().equals(owner) || (by instanceof KageBunshinEntity k && owner.equals(k.owner)))) {
            return false;
        }
        boolean hurt = super.hurt(src, amount);
        if (!level().isClientSide && hurt && (++hits >= 2 || !isAlive() || getHealth() <= 0)) poof();
        return hurt;
    }

    @Override
    public void die(DamageSource src) {
        if (!level().isClientSide && !isRemoved()) {
            puff();
            discard();
            return;
        }
        super.die(src);
    }

    /** Some numa nuvem de fumaça. */
    public void poof() {
        if (isRemoved()) return;
        puff();
        discard();
    }

    private void puff() {
        if (!(level() instanceof ServerLevel sl)) return;
        sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.0, getZ(), 24, 0.35, 0.6, 0.35, 0.04);
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1.0, getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (owner != null && (other.getUUID().equals(owner) || (other instanceof KageBunshinEntity k && owner.equals(k.owner)))) return true;
        return super.isAlliedTo(other);
    }

    // =====================================================================================
    //  Dono
    // =====================================================================================
    @Nullable
    @Override
    public UUID getOwnerUUID() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    @Nullable
    public LivingEntity ownerEntity() {
        UUID id = getOwnerUUID();
        if (id == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getEntity(id) instanceof LivingEntity le ? le : null;
    }

    public static void clearAll() {
        BY_OWNER.clear();
    }

    /** Segue o dono quando não tem o que fazer (e volta se ficar a mais de 6 blocos). */
    private class FollowOwnerGoal extends Goal {
        FollowOwnerGoal() {
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            LivingEntity o = ownerEntity();
            return o != null && getTarget() == null && distanceToSqr(o) > 36;
        }

        @Override
        public void tick() {
            LivingEntity o = ownerEntity();
            if (o != null && tickCount % 10 == 0) getNavigation().moveTo(o, 1.2);
        }
    }

    /** Ataca o foco da magia, quem o dono acertou, quem acertou o dono ou quem mira no dono. */
    private class OwnerFoeGoal extends Goal {
        OwnerFoeGoal() {
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (tickCount % 5 != 0) return false;
            LivingEntity o = ownerEntity();
            if (o == null || LiUtil.calm(o)) return false;
            LivingEntity t = pick(o);
            if (t == null || t == getTarget()) return false;
            setTarget(t);
            return false;
        }

        @Nullable
        private LivingEntity pick(LivingEntity o) {
            if (focus != null && focus.isAlive() && LiUtil.hostile(o, focus)) return focus;
            LivingEntity cur = getTarget();
            if (cur != null && cur.isAlive() && LiUtil.hostile(o, cur)) return null;
            LivingEntity hurtBy = o.getLastHurtByMob();
            if (hurtBy != null && hurtBy.isAlive() && LiUtil.hostile(o, hurtBy) && hurtBy.distanceToSqr(KageBunshinEntity.this) < 24 * 24) return hurtBy;
            LivingEntity hit = o.getLastHurtMob();
            if (hit != null && hit.isAlive() && LiUtil.hostile(o, hit) && hit.distanceToSqr(KageBunshinEntity.this) < 24 * 24) return hit;
            AABB box = getBoundingBox().inflate(14);
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (Mob m : level().getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && (m.getTarget() == o
                    || (m.getTarget() instanceof KageBunshinEntity k && o.getUUID().equals(k.getOwnerUUID()))))) {
                if (!LiUtil.hostile(o, m)) continue;
                double d = m.distanceToSqr(KageBunshinEntity.this);
                if (d < bestD) {
                    bestD = d;
                    best = m;
                }
            }
            return best;
        }
    }

    /** Usado pelo renderer: o tempo de "vida" restante não importa no cliente. */
    public float lifeFraction() {
        return Mth.clamp(life / 300F, 0F, 1F);
    }
}
