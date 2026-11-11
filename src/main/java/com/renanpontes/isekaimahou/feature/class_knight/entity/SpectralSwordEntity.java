package com.renanpontes.isekaimahou.feature.class_knight.entity;

import com.renanpontes.isekaimahou.feature.class_knight.KnightRegistries;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * Espada espectral (só visual): a lâmina do conjurador, gigante e luminosa, que cai do céu na Chuva de Espadas e na
 * Sentença do Juramento. Cai em linha reta até {@code stopY} (o mesmo cálculo roda no cliente, então a queda é lisa),
 * fica cravada alguns ticks e some. Não é salva no mundo nem pode ser atingida; o dano é aplicado por quem a criou.
 */
public class SpectralSwordEntity extends Entity {
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(SpectralSwordEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(SpectralSwordEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(SpectralSwordEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> STOP_Y = SynchedEntityData.defineId(SpectralSwordEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FALL = SynchedEntityData.defineId(SpectralSwordEntity.class, EntityDataSerializers.FLOAT);
    /** Tempo de vida máximo (segurança: nunca fica para trás). */
    private static final int MAX_LIFE = 120;

    private int landedTicks = -1;

    public SpectralSwordEntity(EntityType<? extends SpectralSwordEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Cria a espada em {@code from} caindo até a altura {@code stopY} a {@code speed} blocos/tick. */
    public static SpectralSwordEntity create(ServerLevel level, Vec3 from, double stopY, float speed, ItemStack sword, float scale, int rgb) {
        SpectralSwordEntity e = new SpectralSwordEntity(KnightRegistries.SPECTRAL_SWORD.get(), level);
        e.setPos(from.x, from.y, from.z);
        e.setYRot(level.random.nextFloat() * 360F);
        e.entityData.set(ITEM, sword.isEmpty() ? ItemStack.EMPTY : sword.copyWithCount(1));
        e.entityData.set(SCALE, scale);
        e.entityData.set(COLOR, rgb & 0xFFFFFF);
        e.entityData.set(STOP_Y, (float) stopY);
        e.entityData.set(FALL, speed);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(SCALE, 1.0F);
        builder.define(COLOR, 0xFFFFFF);
        builder.define(STOP_Y, 0.0F);
        builder.define(FALL, 1.0F);
    }

    public ItemStack sword() { return entityData.get(ITEM); }
    public float scale() { return entityData.get(SCALE); }
    public int color() { return entityData.get(COLOR); }
    public boolean landed() { return getY() <= entityData.get(STOP_Y) + 1.0E-3; }

    @Override
    public void tick() {
        super.tick();
        double stop = entityData.get(STOP_Y);
        if (getY() > stop) {
            double ny = Math.max(stop, getY() - entityData.get(FALL));
            setPos(getX(), ny, getZ());
        } else if (landedTicks < 0) {
            landedTicks = 0;
        }
        if (landedTicks >= 0) landedTicks++;
        if (level().isClientSide) {
            clientParticles();
        } else if (tickCount > MAX_LIFE || landedTicks > 14) {
            discard();
        }
    }

    private void clientParticles() {
        int rgb = color();
        Vector3f c = new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
        float s = scale();
        if (landedTicks < 0) {
            for (int i = 0; i < 2; i++) {
                level().addParticle(new DustParticleOptions(c, 0.9F), getX() + (random.nextDouble() - 0.5) * 0.3 * s,
                        getY() + random.nextDouble() * 2.0 * s, getZ() + (random.nextDouble() - 0.5) * 0.3 * s, 0, 0.02, 0);
            }
        } else if (landedTicks % 3 == 0) {
            level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.8 * s, getZ(), 0, 0.03, 0);
        }
    }

    /** Ticks desde que cravou no chão (-1 = ainda caindo). */
    public int landedTicks() {
        return landedTicks;
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
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
