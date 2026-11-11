package com.renanpontes.isekaimahou.feature.legends_c.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;

/**
 * Base das entidades visuais das Técnicas Malditas: sem física, sem colisão, não atingível, não salva (o tipo é
 * {@code noSave}), com o id de rede do dono sincronizado para o cliente desenhar presa a ele. A magia (contexto)
 * fica só na memória do servidor: se a entidade some (chunk descarregado), a técnica acaba junto.
 */
public abstract class LcEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(LcEntity.class, EntityDataSerializers.INT);

    @Nullable protected SpellContext ctx;
    @Nullable protected LivingEntity owner;

    protected LcEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    protected void bindOwner(@Nullable LivingEntity owner) {
        this.owner = owner;
        entityData.set(DATA_OWNER, owner == null ? -1 : owner.getId());
    }

    @Override
    protected final void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, -1);
        defineExtra(builder);
    }

    protected void defineExtra(SynchedEntityData.Builder builder) {}

    /** Dono (no servidor: o guardado; no cliente: pelo id de rede). */
    @Nullable
    public LivingEntity ownerEntity() {
        if (!level().isClientSide) return owner;
        Entity e = level().getEntity(entityData.get(DATA_OWNER));
        return e instanceof LivingEntity le ? le : null;
    }

    // ---------------- É magia: sem física nem interação ----------------
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
    public boolean shouldBeSaved() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
