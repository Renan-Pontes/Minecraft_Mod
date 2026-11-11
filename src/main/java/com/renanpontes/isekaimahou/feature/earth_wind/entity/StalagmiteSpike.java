package com.renanpontes.isekaimahou.feature.earth_wind.entity;

import com.renanpontes.isekaimahou.feature.earth_wind.EwRegistries;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Espinho de pedra (Estalagmite) que brota do chao e afunda de novo. So visual: o dano e aplicado pelo glifo.
 * O renderizador desenha estalactites pontudas subindo conforme {@link #rise}.
 */
public class StalagmiteSpike extends Entity {
    public static final int LIFE = 34;
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(StalagmiteSpike.class, EntityDataSerializers.FLOAT);

    public StalagmiteSpike(EntityType<? extends StalagmiteSpike> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void spawn(ServerLevel level, Vec3 base, float scale, BlockState ground) {
        StalagmiteSpike s = new StalagmiteSpike(EwRegistries.STALAGMITE_SPIKE.get(), level);
        s.entityData.set(SCALE, scale);
        s.moveTo(base.x, base.y, base.z, level.random.nextFloat() * 360F, 0);
        level.addFreshEntity(s);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), base.x, base.y + 0.1, base.z, 30, 0.5 * scale, 0.1,
                0.5 * scale, 0.2);
        level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground), base.x, base.y, base.z, 12, 0.4, 0, 0.4, 0.1);
    }

    public float scale() {
        return entityData.get(SCALE);
    }

    /** Quanto do espinho esta acima do chao (0..1): sobe rapido, fica, e afunda nos ultimos ticks. */
    public float rise(float partialTick) {
        float t = tickCount + partialTick;
        if (t < 4) return Mth.clamp(t / 4F, 0F, 1F);
        if (t > LIFE - 10) return Mth.clamp((LIFE - t) / 10F, 0F, 1F);
        return 1F;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LIFE) discard();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SCALE, 1.0F);
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
