package com.renanpontes.isekaimahou.feature.legends_d.entity;

import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.glyph.SpellContext;

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

/**
 * Base das entidades visuais do legends_d. São TEMPORÁRIAS: não são salvas (somem ao descarregar o chunk ou
 * reiniciar), guardam a magia só na memória e se desfazem sozinhas se perderem o contexto. Sem física nem colisão.
 */
public abstract class LdSpellEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(LdSpellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_CASTER = SynchedEntityData.defineId(LdSpellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(LdSpellEntity.class, EntityDataSerializers.INT);

    @Nullable
    protected SpellContext ctx;
    @Nullable
    protected UUID casterId;
    /** Idade em ticks (no cliente corrigida pelo servidor de tempos em tempos). */
    protected int age;

    protected LdSpellEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    protected void bind(SpellContext ctx, int rgb) {
        this.ctx = ctx;
        this.casterId = ctx.casterId();
        LivingEntity c = ctx.caster();
        entityData.set(DATA_CASTER, c != null ? c.getId() : -1);
        entityData.set(DATA_COLOR, rgb & 0xFFFFFF);
    }

    @Override
    protected final void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_AGE, 0);
        builder.define(DATA_CASTER, -1);
        builder.define(DATA_COLOR, 0xFFFFFF);
        defineExtraData(builder);
    }

    protected void defineExtraData(SynchedEntityData.Builder builder) {}

    public int age() { return age; }

    public int color() { return entityData.get(DATA_COLOR); }

    /** Id de rede de quem conjurou (−1 se não houver). */
    public int casterNetId() { return entityData.get(DATA_CASTER); }

    @Nullable
    public Entity casterEntity() {
        int id = casterNetId();
        return id < 0 ? null : level().getEntity(id);
    }

    @Nullable
    public UUID casterUuid() { return casterId; }

    protected void syncAge() { entityData.set(DATA_AGE, age); }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (age % 10 == 0) syncAge();
        if (ctx == null) {
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
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }

    @Override
    public boolean displayFireAnimation() { return false; }

    // Temporária: nada é salvo (o tipo é noSave); ler um NBT velho só a faz sumir.
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (!level().isClientSide) discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
