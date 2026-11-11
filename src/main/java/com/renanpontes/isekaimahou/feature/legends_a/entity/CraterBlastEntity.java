package com.renanpontes.isekaimahou.feature.legends_a.entity;

import com.renanpontes.isekaimahou.feature.legends_a.LegendFx;
import com.renanpontes.isekaimahou.feature.legends_a.LegendsARegistry;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * O "corpo" de uma Explosão Colossal: o renderer desenha a bola de fogo, a coluna de fogo, a nuvem em cogumelo e a
 * onda de choque; no servidor ela solta as partículas de longe. v0.8: a cratera de verdade é da API do núcleo
 * ({@code Destruction.crater}, pedida pelo {@code CraterQueue}) — esta entidade é só o espetáculo e some sozinha
 * (não é salva: descarregar o chunk a apaga).
 */
public class CraterBlastEntity extends Entity {
    public static final int LIFETIME = 110;

    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(CraterBlastEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(CraterBlastEntity.class, EntityDataSerializers.INT);

    public CraterBlastEntity(EntityType<? extends CraterBlastEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static CraterBlastEntity create(ServerLevel level, Vec3 at, float radius, int color) {
        CraterBlastEntity e = new CraterBlastEntity(LegendsARegistry.CRATER_BLAST.get(), level);
        e.setPos(at.x, at.y, at.z);
        e.entityData.set(DATA_RADIUS, radius);
        e.entityData.set(DATA_COLOR, color);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 4.0F);
        builder.define(DATA_COLOR, LegendFx.CRIMSON);
    }

    public float radius() { return entityData.get(DATA_RADIUS); }
    public int color() { return entityData.get(DATA_COLOR); }
    /** Altura da coluna/cogumelo. */
    public float columnHeight() { return radius() * 3.0F + 6.0F; }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        serverParticles(sl);
        if (tickCount == 8) LegendFx.sound(sl, position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 6.0F, 0.45F);
        if (tickCount == 24) LegendFx.sound(sl, position(), SoundEvents.FIRECHARGE_USE, 4.0F, 0.4F);
        if (tickCount >= LIFETIME) discard();
    }

    /** Partículas de longe que acompanham o desenho: brasas subindo pela coluna, fumaça no topo, poeira no chão. */
    private void serverParticles(ServerLevel sl) {
        float R = radius();
        float H = columnHeight();
        Vec3 c = position();
        int t = tickCount;
        if (t <= 10) {
            double ring = R * (0.6 + t * 0.25);
            for (int i = 0; i < 24; i++) {
                double a = Math.PI * 2 * i / 24 + t * 0.1;
                LegendFx.far(sl, ParticleTypes.POOF, c.x + Math.cos(a) * ring, c.y + 0.3, c.z + Math.sin(a) * ring, 1,
                        0.2, 0.1, 0.2, 0.05);
            }
        }
        if (t <= 45 && t % 2 == 0) {
            float h = H * Math.min(1F, t / 25F);
            for (int i = 0; i < 6; i++) {
                double y = c.y + random.nextDouble() * h;
                LegendFx.far(sl, ParticleTypes.FLAME, c.x, y, c.z, 3, R * 0.18, 0.4, R * 0.18, 0.06);
            }
            LegendFx.far(sl, ParticleTypes.LAVA, c.x, c.y + 1, c.z, 3, R * 0.3, 0.5, R * 0.3, 0);
            LegendFx.far(sl, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.x, c.y + h * 0.5, c.z, 2, R * 0.25, h * 0.3, R * 0.25, 0.02);
        }
        if (t >= 12 && t <= 75 && t % 3 == 0) {
            float cap = Math.min(1F, (t - 12) / 30F);
            double rr = R * (0.5 + 0.9 * cap);
            for (int i = 0; i < 10; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                double d = rr * Math.sqrt(random.nextDouble());
                LegendFx.far(sl, ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x + Math.cos(a) * d, c.y + H + (random.nextDouble() - 0.3) * R * 0.5,
                        c.z + Math.sin(a) * d, 1, 0.3, 0.3, 0.3, 0.01);
            }
            LegendFx.far(sl, ParticleTypes.LARGE_SMOKE, c.x, c.y + H, c.z, 4, rr * 0.5, R * 0.2, rr * 0.5, 0.02);
        }
    }

    private void clientTick() {
        if (tickCount > 60 || random.nextInt(2) != 0) return;
        float R = radius();
        Vec3 c = position();
        double a = random.nextDouble() * Math.PI * 2;
        double d = R * (0.3 + random.nextDouble());
        level().addParticle(ParticleTypes.FLAME, c.x + Math.cos(a) * d, c.y + 0.2, c.z + Math.sin(a) * d, 0, 0.08, 0);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        float R = radius() * 3.6F;
        Vec3 p = position();
        return new AABB(p.x - R, p.y - 2, p.z - R, p.x + R, p.y + columnHeight() + radius() * 1.5, p.z + R);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 384 * 384;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    /** Só espetáculo: não vai para o disco (um chunk salvo no meio da explosão não a ressuscita). */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
