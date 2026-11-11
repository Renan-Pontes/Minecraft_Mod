package com.renanpontes.isekaimahou.feature.class_gunslinger.entity;

import com.renanpontes.isekaimahou.feature.class_gunslinger.GsRegistries;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Corte da Espada de Fótons (só visual): um crescente de luz que nasce na frente de quem corta, gira no plano do golpe
 * e some em poucos ticks. A direção (yaw/pitch), a inclinação do corte (roll), o tamanho e a cor vêm do servidor.
 * Nunca é salvo.
 */
public class PhotonSlashEntity extends Entity {
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(PhotonSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PITCH = SynchedEntityData.defineId(PhotonSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData.defineId(PhotonSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(PhotonSlashEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(PhotonSlashEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(PhotonSlashEntity.class, EntityDataSerializers.INT);

    private boolean initialized;

    public PhotonSlashEntity(EntityType<? extends PhotonSlashEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Cria o corte em {@code at}, virado para {@code dir}; {@code roll} em graus (0 = horizontal). */
    public static void spawn(ServerLevel level, Vec3 at, Vec3 dir, float roll, float size, int rgb, int life) {
        PhotonSlashEntity e = new PhotonSlashEntity(GsRegistries.PHOTON_SLASH.get(), level);
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        e.entityData.set(DATA_YAW, (float) Math.atan2(d.x, d.z));
        e.entityData.set(DATA_PITCH, (float) Math.asin(Math.max(-1, Math.min(1, d.y))));
        e.entityData.set(DATA_ROLL, roll);
        e.entityData.set(DATA_SIZE, size);
        e.entityData.set(DATA_COLOR, rgb);
        e.entityData.set(DATA_LIFE, Math.max(3, life));
        e.initialized = true;
        e.setPos(at.x, at.y, at.z);
        level.addFreshEntity(e);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_YAW, 0F);
        builder.define(DATA_PITCH, 0F);
        builder.define(DATA_ROLL, 0F);
        builder.define(DATA_SIZE, 2.5F);
        builder.define(DATA_COLOR, 0xB98CFF);
        builder.define(DATA_LIFE, 6);
    }

    /** Direção do corte em radianos (yaw: atan2(x, z); pitch: asin(y)). */
    public float slashYaw() { return entityData.get(DATA_YAW); }
    public float slashPitch() { return entityData.get(DATA_PITCH); }
    public float roll() { return entityData.get(DATA_ROLL); }
    public float size() { return entityData.get(DATA_SIZE); }
    public int color() { return entityData.get(DATA_COLOR); }
    public int life() { return entityData.get(DATA_LIFE); }

    /** 0..1 do golpe (o arco "varre" na primeira metade e apaga na segunda). */
    public float progress(float partial) {
        return Math.min(1F, (tickCount + partial) / Math.max(1, life()));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (!initialized || tickCount > life())) discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 96 * 96;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        initialized = false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
