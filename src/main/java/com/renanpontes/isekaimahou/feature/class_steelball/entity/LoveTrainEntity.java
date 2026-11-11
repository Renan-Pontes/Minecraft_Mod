package com.renanpontes.isekaimahou.feature.class_steelball.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_steelball.SbLoveTrain;
import com.renanpontes.isekaimahou.feature.class_steelball.SbRegistry;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * LOVE TRAIN — a cortina de luz dimensional que envolve o portador: acompanha o dono a cada tick (o cliente desenha as
 * lâminas de luz douradas-brancas com frestas, ver {@code LoveTrainRenderer}). Não tem física, não é atingível e não é
 * salva: se o dono some ou a técnica acaba, ela se desfaz. Sincroniza o dono, a duração total (para o brilho aparecer e
 * sumir) e se a luz está FURADA (Acerto Garantido de um domínio: as lâminas piscam).
 */
public class LoveTrainEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(LoveTrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(LoveTrainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_PIERCED = SynchedEntityData.defineId(LoveTrainEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable private LivingEntity owner;
    /** Só o vislumbre do despertar: dura {@link #life()} ticks e não depende da técnica estar ativa. */
    private boolean preview;

    public LoveTrainEntity(EntityType<? extends LoveTrainEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public static LoveTrainEntity spawn(ServerLevel level, LivingEntity owner, int life) {
        LoveTrainEntity e = new LoveTrainEntity(SbRegistry.LOVE_TRAIN.get(), level);
        e.owner = owner;
        e.entityData.set(DATA_OWNER, owner.getId());
        e.entityData.set(DATA_LIFE, Math.max(1, life));
        e.setPos(owner.getX(), owner.getY(), owner.getZ());
        level.addFreshEntity(e);
        return e;
    }

    /** O vislumbre da cortina no despertar do Love Train (só visual, sem proteção). */
    public static LoveTrainEntity spawnPreview(ServerLevel level, LivingEntity owner, int life) {
        LoveTrainEntity e = new LoveTrainEntity(SbRegistry.LOVE_TRAIN.get(), level);
        e.owner = owner;
        e.preview = true;
        e.entityData.set(DATA_OWNER, owner.getId());
        e.entityData.set(DATA_LIFE, Math.max(1, life));
        e.setPos(owner.getX(), owner.getY(), owner.getZ());
        level.addFreshEntity(e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_LIFE, 240);
        builder.define(DATA_PIERCED, false);
    }

    /** Duração total (ticks) — o cliente usa para o brilho sumir no fim. */
    public int life() {
        return entityData.get(DATA_LIFE);
    }

    public void setLife(int life) {
        if (life() != life) entityData.set(DATA_LIFE, Math.max(1, life));
    }

    public boolean pierced() {
        return entityData.get(DATA_PIERCED);
    }

    public void setPierced(boolean p) {
        if (pierced() != p) entityData.set(DATA_PIERCED, p);
    }

    /** Dono (no servidor: o guardado; no cliente: pelo id de rede). */
    @Nullable
    public LivingEntity ownerEntity() {
        if (!level().isClientSide) return owner;
        Entity e = level().getEntity(entityData.get(DATA_OWNER));
        return e instanceof LivingEntity le ? le : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        boolean alive = owner != null && (preview ? tickCount <= life() : SbLoveTrain.active(owner));
        if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != level() || !alive
                || tickCount > life() + 40) {
            discard();
            return;
        }
        setPos(owner.getX(), owner.getY(), owner.getZ());
    }

    /** Fagulhas de luz subindo pelas frestas da cortina. */
    private void clientTick() {
        LivingEntity o = ownerEntity();
        if (o == null || random.nextInt(2) != 0) return;
        double a = random.nextDouble() * Math.PI * 2;
        double r = SbLoveTrain.CURTAIN_RADIUS + o.getBbWidth() * 0.5 + (random.nextDouble() - 0.5) * 0.5;
        Vec3 p = o.position().add(Math.cos(a) * r, random.nextDouble() * (o.getBbHeight() + 0.6), Math.sin(a) * r);
        level().addParticle(pierced() ? ParticleTypes.CRIT : ParticleTypes.END_ROD, p.x, p.y, p.z, 0, 0.03, 0);
    }

    // ---------------- É luz: sem física nem interação ----------------
    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }

    @Override
    public boolean isIgnoringBlockTriggers() { return true; }

    @Override
    public boolean canUsePortal(boolean allowPassengers) { return false; }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
