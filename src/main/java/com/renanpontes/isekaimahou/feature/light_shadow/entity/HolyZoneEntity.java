package com.renanpontes.isekaimahou.feature.light_shadow.entity;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MahouConfig;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Base das zonas de Luz (Barreira Sagrada e Santuário): uma entidade invisível e parada que guarda o dono,
 * o raio, a duração e o poder. O servidor aplica as regras; o cliente desenha as partículas.
 */
public abstract class HolyZoneEntity extends Entity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(HolyZoneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DURATION = SynchedEntityData.defineId(HolyZoneEntity.class, EntityDataSerializers.INT);

    protected UUID owner;
    protected float power = 1.0F;
    protected int age;

    protected HolyZoneEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Configura a zona logo depois de criar (antes de addFreshEntity). */
    public void setup(UUID owner, float radius, int duration, float power) {
        this.owner = owner;
        this.power = power;
        entityData.set(RADIUS, radius);
        entityData.set(DURATION, duration);
    }

    public float radius() { return entityData.get(RADIUS); }
    public int duration() { return entityData.get(DURATION); }
    public UUID owner() { return owner; }

    /** 0..1: quanto da zona já passou (para o brilho diminuir no fim). */
    public float progress() {
        return Math.min(1F, tickCount / (float) Math.max(1, duration()));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 4.0F);
        builder.define(DURATION, 200);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (age == 0) onStart(sl);
        if (++age > duration()) {
            onEnd(sl);
            discard();
            return;
        }
        serverTick(sl);
    }

    protected abstract void onStart(ServerLevel level);
    protected abstract void serverTick(ServerLevel level);
    protected abstract void onEnd(ServerLevel level);
    protected abstract void clientTick();

    /** Entidades dentro da esfera da zona. */
    protected <T extends Entity> List<T> inside(Class<T> cls, double extra) {
        double r = radius() + extra;
        Vec3 c = position();
        return level().getEntitiesOfClass(cls, new AABB(c, c).inflate(r), e -> e.distanceToSqr(c) <= r * r);
    }

    /**
     * Limita as zonas do mesmo dono (as mais antigas somem). Usa o limite de magias persistentes da config,
     * contando as zonas deste tipo perto do dono.
     */
    public static void limit(ServerLevel level, UUID owner, Vec3 near, EntityType<?> type) {
        if (owner == null) return;
        int max = Math.max(1, Math.min(3, MahouConfig.MAX_PERSISTENT_SPELLS.get()));
        List<HolyZoneEntity> mine = level.getEntitiesOfClass(HolyZoneEntity.class, new AABB(near, near).inflate(96),
                z -> z.getType() == type && owner.equals(z.owner) && z.isAlive());
        if (mine.size() < max) return;
        mine.sort(Comparator.comparingInt((HolyZoneEntity z) -> z.age).reversed());
        for (int i = 0; i <= mine.size() - max; i++) {
            HolyZoneEntity z = mine.get(i);
            z.onEnd(level);
            z.discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
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
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
        power = tag.getFloat("power");
        age = tag.getInt("age");
        entityData.set(RADIUS, tag.getFloat("radius"));
        entityData.set(DURATION, tag.getInt("duration"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("owner", owner);
        tag.putFloat("power", power);
        tag.putInt("age", age);
        tag.putFloat("radius", radius());
        tag.putInt("duration", duration());
    }
}
