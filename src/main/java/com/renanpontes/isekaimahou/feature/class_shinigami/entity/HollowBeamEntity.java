package com.renanpontes.isekaimahou.feature.class_shinigami.entity;

import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiRegistries;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * O feixe do Cero (só visual): um raio grosso de Reiatsu rubro com núcleo negro, do ponto de saída até o fim, que
 * aparece de uma vez, pulsa e some em {@link #LIFE} ticks. Toda a lógica (dano, efeitos) é feita na hora pela forma;
 * a entidade só desenha. Nunca é salva (some ao descarregar).
 */
public class HollowBeamEntity extends Entity {
    public static final int LIFE = 16;
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(HollowBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(HollowBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(HollowBeamEntity.class, EntityDataSerializers.INT);

    public HollowBeamEntity(EntityType<? extends HollowBeamEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Cria o feixe de {@code from} até {@code to} (depois: {@code addFreshEntity}). */
    public static HollowBeamEntity of(ServerLevel level, Vec3 from, Vec3 to, float width, int rgb) {
        HollowBeamEntity b = new HollowBeamEntity(ShinigamiRegistries.HOLLOW_BEAM.get(), level);
        Vec3 d = to.subtract(from);
        b.setPos(from.x, from.y, from.z);
        b.setYRot((float) (Mth.atan2(d.x, d.z) * Mth.RAD_TO_DEG));
        b.setXRot((float) (Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG));
        b.yRotO = b.getYRot();
        b.xRotO = b.getXRot();
        b.entityData.set(LENGTH, (float) Math.max(0.5, d.length()));
        b.entityData.set(WIDTH, width);
        b.entityData.set(COLOR, rgb);
        return b;
    }

    public float length() {
        return entityData.get(LENGTH);
    }

    public float width() {
        return entityData.get(WIDTH);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LENGTH, 8.0F);
        builder.define(WIDTH, 1.0F);
        builder.define(COLOR, 0xE01030);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LIFE) discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 160 * 160;
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
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
