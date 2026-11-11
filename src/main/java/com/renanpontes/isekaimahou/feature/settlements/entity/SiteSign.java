package com.renanpontes.isekaimahou.feature.settlements.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;

/**
 * Letreiro flutuante sobre uma obra ("⚒ Mercado — 45%" com barra de progresso). Não é salvo no mundo: o coração da
 * cidade cria/atualiza/remove a cada 2 s e, sem notícias do coração por 10 s, ele some sozinho.
 */
public class SiteSign extends Entity {
    private static final EntityDataAccessor<Component> DATA_TITLE = SynchedEntityData.defineId(SiteSign.class, EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Float> DATA_PROGRESS = SynchedEntityData.defineId(SiteSign.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(SiteSign.class, EntityDataSerializers.INT);
    private int life = 200;

    public SiteSign(EntityType<? extends SiteSign> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TITLE, Component.empty());
        builder.define(DATA_PROGRESS, 0.0F);
        builder.define(DATA_COLOR, 0xFFD86B);
    }

    public void update(Component title, float progress, int color) {
        if (!title.equals(entityData.get(DATA_TITLE))) entityData.set(DATA_TITLE, title);
        if (Math.abs(progress - entityData.get(DATA_PROGRESS)) > 0.001F) entityData.set(DATA_PROGRESS, progress);
        if (color != entityData.get(DATA_COLOR)) entityData.set(DATA_COLOR, color);
        life = 200;
    }

    public Component title() {
        return entityData.get(DATA_TITLE);
    }

    public float progress() {
        return entityData.get(DATA_PROGRESS);
    }

    public int color() {
        return entityData.get(DATA_COLOR);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && --life <= 0) discard();
    }

    @Override
    public boolean shouldBeSaved() {
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
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
