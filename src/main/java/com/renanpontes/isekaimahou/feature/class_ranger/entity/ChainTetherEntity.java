package com.renanpontes.isekaimahou.feature.class_ranger.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_ranger.RangerFx;
import com.renanpontes.isekaimahou.feature.class_ranger.RangerRegistries;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * CORRENTE CONJURADA (homenagem ao caçador das correntes): uma corrente de verdade (a textura da corrente do jogo,
 * desenhada por {@code ChainTetherRenderer}) sai da mão do Ranger até o alvo.
 * <ul>
 *   <li><b>Prisão</b>: o alvo fica preso no lugar onde foi pego (a corrente o puxa de volta se ele passar de 1,5 bloco),
 *   lento, e não consegue se teletransportar (endermen, pérolas, coros); se o Ranger se afastar mais de 32 blocos ou
 *   morrer, a corrente se parte.</li>
 *   <li><b>Sagrada</b>: corrente dourada curta, só o visual da cura passando para o aliado.</li>
 * </ul>
 * Não é salva no mundo e some sozinha.
 */
public class ChainTetherEntity extends Entity {
    public static final String ID = "ranger_chain_tether";
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(ChainTetherEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(ChainTetherEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_HOLY = SynchedEntityData.defineId(ChainTetherEntity.class, EntityDataSerializers.BOOLEAN);

    /** Alvos presos agora (para bloquear teletransporte). */
    private static final Map<UUID, ChainTetherEntity> BOUND = new HashMap<>();

    @Nullable
    private UUID ownerId;
    @Nullable
    private UUID targetId;
    private Vec3 anchor = Vec3.ZERO;
    private int life = 100;

    public ChainTetherEntity(EntityType<? extends ChainTetherEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Prende {@code target} com uma corrente que sai de {@code owner} por {@code ticks}. */
    public static ChainTetherEntity bind(ServerLevel level, LivingEntity owner, LivingEntity target, int ticks) {
        ChainTetherEntity old = BOUND.get(target.getUUID());
        if (old != null && !old.isRemoved()) old.discard();
        ChainTetherEntity e = create(level, owner, target, ticks, false);
        BOUND.put(target.getUUID(), e);
        level.addFreshEntity(e);
        return e;
    }

    /** Corrente dourada (só visual) até um aliado curado. */
    public static void holy(ServerLevel level, LivingEntity owner, LivingEntity target) {
        level.addFreshEntity(create(level, owner, target, 16, true));
    }

    private static ChainTetherEntity create(ServerLevel level, LivingEntity owner, LivingEntity target, int ticks, boolean holy) {
        ChainTetherEntity e = new ChainTetherEntity(RangerRegistries.CHAIN_TETHER.get(), level);
        e.ownerId = owner.getUUID();
        e.targetId = target.getUUID();
        e.anchor = target.position();
        e.life = Math.max(10, ticks);
        e.entityData.set(DATA_OWNER, owner.getId());
        e.entityData.set(DATA_TARGET, target.getId());
        e.entityData.set(DATA_HOLY, holy);
        e.setPos(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
        return e;
    }

    /** O alvo está preso por uma corrente? */
    public static boolean isBound(Entity e) {
        ChainTetherEntity c = BOUND.get(e.getUUID());
        return c != null && !c.isRemoved();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_TARGET, -1);
        builder.define(DATA_HOLY, false);
    }

    @Nullable
    public Entity owner() {
        return level().getEntity(entityData.get(DATA_OWNER));
    }

    @Nullable
    public Entity target() {
        return level().getEntity(entityData.get(DATA_TARGET));
    }

    public boolean holy() {
        return entityData.get(DATA_HOLY);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        LivingEntity owner = ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity le ? le : null;
        LivingEntity target = targetId != null && sl.getEntity(targetId) instanceof LivingEntity le ? le : null;
        if (owner == null || target == null || !owner.isAlive() || !target.isAlive() || tickCount > life
                || owner.distanceToSqr(target) > 32 * 32) {
            shatter(sl);
            return;
        }
        setPos(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
        if (holy()) return;
        double d = target.position().distanceTo(anchor);
        if (d > 1.5) {
            Vec3 back = anchor.subtract(target.position()).normalize().scale(Math.min(0.6, 0.15 + d * 0.1));
            target.setDeltaMovement(target.getDeltaMovement().scale(0.4).add(back.x, Math.max(back.y, -0.1), back.z));
            target.hurtMarked = true;
        }
        if (tickCount % 10 == 0) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3, false, false, true), owner);
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 3,
                    0.3, 0.3, 0.3, 0.05);
        }
        if (tickCount % 40 == 0) RangerFx.sound(sl, target.position(), SoundEvents.CHAIN_STEP, 0.8F, 0.7F);
    }

    private void shatter(ServerLevel sl) {
        if (!holy()) {
            sl.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 10, 0.3, 0.3, 0.3, 0.2);
            RangerFx.sound(sl, position(), SoundEvents.CHAIN_BREAK, 1.0F, 1.0F);
        }
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (targetId != null && BOUND.get(targetId) == this) BOUND.remove(targetId);
        super.remove(reason);
    }

    public static void clearAll() {
        BOUND.clear();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
