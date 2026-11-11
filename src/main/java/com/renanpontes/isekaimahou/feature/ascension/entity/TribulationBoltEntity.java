package com.renanpontes.isekaimahou.feature.ascension.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Raio arcano da Tribulação: SÓ visual (o dano e o som são aplicados pela Tribulação).
 * Não usamos o LightningBolt vanilla porque ele toca o trovão em volume 10000 para a dimensão inteira —
 * com um raio a cada 1–2 s isso ensurdeceria o servidor todo. O cliente desenha um raio roxo.
 */
public class TribulationBoltEntity extends Entity {
    public static final int LIFE = 8;
    /** Semente da forma do raio (cada cliente sorteia a sua, como no vanilla). */
    public final long seed;

    public TribulationBoltEntity(EntityType<? extends TribulationBoltEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
        this.seed = random.nextLong();
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LIFE) discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = 96.0 * getViewScale();
        return distance < d * d;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
