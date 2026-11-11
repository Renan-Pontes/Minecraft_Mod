package com.renanpontes.isekaimahou.feature.legends_f.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

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
 * Base das entidades de magia do legends_f (Barreira, Mãos da Inveja, Deus da Floresta). TEMPORÁRIAS (tipo
 * {@code noSave}: somem ao descarregar o chunk ou reiniciar) e guardam a magia só na memória. Idade e duração
 * sincronizadas com o cliente (o renderer anima por elas). Não colidem, não são atingíveis, não usam portais.
 */
public abstract class LfSpellEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(LfSpellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(LfSpellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(LfSpellEntity.class, EntityDataSerializers.INT);

    @Nullable
    protected SpellContext ctx;
    @Nullable
    protected SpellStats stats;
    protected int age;

    protected LfSpellEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** Liga a entidade à magia (antes de {@code addFreshEntity}). */
    protected void bind(SpellContext ctx, @Nullable SpellStats stats, int color, int life) {
        this.ctx = ctx;
        this.stats = stats;
        entityData.set(DATA_COLOR, color & 0xFFFFFF);
        entityData.set(DATA_LIFE, Math.max(1, life));
    }

    @Override
    protected final void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR, 0xFFFFFF);
        builder.define(DATA_AGE, 0);
        builder.define(DATA_LIFE, 100);
        defineExtraData(builder);
    }

    protected void defineExtraData(SynchedEntityData.Builder builder) {}

    public int color() { return entityData.get(DATA_COLOR); }

    public int age() { return age; }

    public int life() { return entityData.get(DATA_LIFE); }

    protected void setLife(int life) { entityData.set(DATA_LIFE, Math.max(1, life)); }

    /** 0..1 da vida já passada (com o tick parcial). */
    public float progress(float pt) {
        return Math.min(1F, (age + pt) / Math.max(1, life()));
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (age % 10 == 0) entityData.set(DATA_AGE, age);
        if (ctx == null || age > life() + 40) {
            discard();
            return;
        }
        serverTick((ServerLevel) level(), ctx);
    }

    protected abstract void serverTick(ServerLevel level, SpellContext ctx);

    protected void clientTick() {}

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == DATA_AGE && level().isClientSide) age = entityData.get(DATA_AGE);
    }

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
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 192 * 192; }

    @Override
    public boolean displayFireAnimation() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
