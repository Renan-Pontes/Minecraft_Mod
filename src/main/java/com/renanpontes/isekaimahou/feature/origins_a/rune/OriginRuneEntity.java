package com.renanpontes.isekaimahou.feature.origins_a.rune;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;

/**
 * Base das runas do Mago (Selo Explosivo, Círculo de Teleporte, Ward): entidade parada no chão, sem física,
 * invulnerável, com dono, cor e tempo de vida (-1 = permanente). O visual é desenhado pelos renderers do cliente.
 */
public abstract class OriginRuneEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(OriginRuneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(OriginRuneEntity.class, EntityDataSerializers.INT);
    /** Ticks que faltam, só enviado no último segundo (para o cliente desbotar a runa). -1 = longe do fim. */
    private static final EntityDataAccessor<Integer> EXPIRE_IN = SynchedEntityData.defineId(OriginRuneEntity.class, EntityDataSerializers.INT);

    protected int age;
    protected int lifetime = -1;

    protected OriginRuneEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(COLOR, 0xB98CFF);
        builder.define(EXPIRE_IN, -1);
    }

    public void setOwner(UUID id) { entityData.set(OWNER, Optional.ofNullable(id)); }
    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public void setColor(int rgb) { entityData.set(COLOR, rgb & 0xFFFFFF); }
    public int color() { return entityData.get(COLOR); }
    public int age() { return age; }
    public void setLifetime(int ticks) { lifetime = ticks; }

    /** Recomeça a contagem com um novo tempo de vida (runa reconjurada no mesmo lugar). */
    public void renew(int ticks) {
        age = 0;
        lifetime = ticks;
        entityData.set(EXPIRE_IN, -1);
    }

    /** Opacidade de entrada/saída (0..1) para os renderers. */
    public float fade(float partialTick) {
        float t = age + partialTick;
        float in = Math.min(1.0F, t / 10.0F);
        int left = entityData.get(EXPIRE_IN);
        float out = left >= 0 ? Math.min(1.0F, Math.max(0.0F, (left - partialTick) / 20.0F)) : 1.0F;
        return in * out;
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (lifetime > 0 && age >= lifetime) {
            onExpire((ServerLevel) level());
            discard();
            return;
        }
        if (lifetime > 0 && lifetime - age <= 20) entityData.set(EXPIRE_IN, lifetime - age);
        serverTick((ServerLevel) level());
    }

    protected abstract void serverTick(ServerLevel level);

    protected void clientTick() {}

    protected void onExpire(ServerLevel level) {}

    // ---------------- Runa não interage fisicamente ----------------
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
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 96 * 96; }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = ownerId();
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Color", color());
        tag.putInt("Age", age);
        tag.putInt("Lifetime", lifetime);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setOwner(tag.hasUUID("Owner") ? tag.getUUID("Owner") : null);
        if (tag.contains("Color")) setColor(tag.getInt("Color"));
        age = tag.getInt("Age");
        lifetime = tag.contains("Lifetime") ? tag.getInt("Lifetime") : -1;
    }

    /** Runas do mesmo tipo e dono carregadas no mundo (para o limite por jogador). */
    protected static <T extends OriginRuneEntity> java.util.List<T> ownedBy(ServerLevel level, EntityType<T> type, UUID owner) {
        java.util.List<T> list = new java.util.ArrayList<>();
        level.getEntities(type, e -> owner != null && owner.equals(e.ownerId()) && e.isAlive()).forEach(list::add);
        return list;
    }

    /** Mantém no máximo {@code max} runas do dono: remove as mais velhas. */
    protected static <T extends OriginRuneEntity> void enforceLimit(ServerLevel level, EntityType<T> type, UUID owner, int max) {
        java.util.List<T> list = ownedBy(level, type, owner);
        list.sort((a, b) -> Integer.compare(b.age, a.age));
        while (list.size() >= max) {
            T oldest = list.remove(0);
            oldest.onExpire(level);
            oldest.discard();
        }
    }
}
