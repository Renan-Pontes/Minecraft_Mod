package com.renanpontes.isekaimahou.feature.bosses_f.entity;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * Caixa de acerto extra de um chefe (cabeça da Hidra, núcleo do Colosso) — como as partes do Dragão do Fim.
 * Não é salva nem desenhada: o chefe a posiciona todo tick (servidor e cliente) e recebe o dano dela.
 */
public class BossPart extends PartEntity<OverworldBoss> {
    private final int index;
    private final EntityDimensions size;

    public BossPart(OverworldBoss parent, int index, float width, float height) {
        super(parent);
        this.index = index;
        this.size = EntityDimensions.scalable(width, height);
        refreshDimensions();
    }

    public int index() {
        return index;
    }

    /** Move a parte guardando a posição anterior (interpolação/colisão). */
    public void moveTo(double x, double y, double z) {
        xo = getX();
        yo = getY();
        zo = getZ();
        xOld = xo;
        yOld = yo;
        zOld = zo;
        setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    /** Só dá para mirar/acertar enquanto a parte existe (cabeça viva, núcleo...). */
    @Override
    public boolean isPickable() {
        return getParent().isPartActive(index) && getParent().isAlive();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || !isPickable()) return false;
        return getParent().hurtPart(this, source, amount);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
