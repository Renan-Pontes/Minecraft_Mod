package com.renanpontes.isekaimahou.feature.legends_a.entity;

import com.renanpontes.isekaimahou.feature.legends_a.LegendFx;
import com.renanpontes.isekaimahou.feature.legends_a.LegendsARegistry;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Círculos mágicos empilhados no céu enquanto alguém conjura uma Explosão Colossal: aparecem um a um durante a
 * conjuração, acima do ponto mirado (e o acompanham). Ao soltar a magia, brilham, giram rápido, ligam-se ao chão
 * por um feixe de luz e somem; se a conjuração for interrompida, se desfazem.
 */
public class ExplosionSigilEntity extends Entity {
    public static final byte CHARGING = 0;
    public static final byte RELEASED = 1;
    public static final byte FIZZLE = 2;

    public static final int RELEASE_TICKS = 40;
    public static final int FIZZLE_TICKS = 12;
    /** Distância vertical entre os círculos. */
    public static final float GAP = 2.4F;

    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(ExplosionSigilEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LAYERS = SynchedEntityData.defineId(ExplosionSigilEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(ExplosionSigilEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Byte> DATA_STATE = SynchedEntityData.defineId(ExplosionSigilEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_CHARGE = SynchedEntityData.defineId(ExplosionSigilEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_BEAM = SynchedEntityData.defineId(ExplosionSigilEntity.class, EntityDataSerializers.FLOAT);

    // servidor
    private long keepAliveUntil;
    private int stateTicks;
    private Vec3 follow;

    // cliente
    public int clientStateStart;

    public ExplosionSigilEntity(EntityType<? extends ExplosionSigilEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Círculos que acompanham uma conjuração de {@code chargeTicks} ticks. */
    public static ExplosionSigilEntity charging(ServerLevel level, Vec3 at, int color, float radius, int layers, int chargeTicks) {
        ExplosionSigilEntity s = new ExplosionSigilEntity(LegendsARegistry.EXPLOSION_SIGIL.get(), level);
        s.setPos(at.x, at.y, at.z);
        s.follow = at;
        s.entityData.set(DATA_COLOR, color);
        s.entityData.set(DATA_RADIUS, radius);
        s.entityData.set(DATA_LAYERS, layers);
        s.entityData.set(DATA_CHARGE, Math.max(1, chargeTicks));
        s.keepAliveUntil = level.getGameTime() + 3;
        return s;
    }

    // =====================================================================================
    //  Dados
    // =====================================================================================
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR, LegendFx.CRIMSON);
        builder.define(DATA_LAYERS, 4);
        builder.define(DATA_RADIUS, 6.0F);
        builder.define(DATA_STATE, CHARGING);
        builder.define(DATA_CHARGE, 40);
        builder.define(DATA_BEAM, 12.0F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (level().isClientSide && DATA_STATE.equals(key)) clientStateStart = tickCount;
    }

    public int color() { return entityData.get(DATA_COLOR); }
    public int layers() { return entityData.get(DATA_LAYERS); }
    public float radius() { return entityData.get(DATA_RADIUS); }
    public byte state() { return entityData.get(DATA_STATE); }
    public int chargeTicks() { return entityData.get(DATA_CHARGE); }
    public float beam() { return entityData.get(DATA_BEAM); }

    /** Fração da conjuração (0..1) — quantos círculos já apareceram (cliente). */
    public float chargeFraction(float partialTick) {
        return Mth.clamp((tickCount + partialTick) / chargeTicks(), 0, 1);
    }

    public float stateAge(float partialTick) {
        return tickCount - clientStateStart + partialTick;
    }

    // =====================================================================================
    //  Controle (servidor)
    // =====================================================================================

    /** A conjuração continua: mantém vivo e segue o novo ponto mirado. */
    public void keepAlive(ServerLevel level, Vec3 at) {
        keepAliveUntil = level.getGameTime() + 3;
        follow = at;
    }

    /** A magia foi solta: brilha, liga ao chão e some. {@code groundDist} = altura do feixe até o chão. */
    public void release(float groundDist) {
        if (state() != CHARGING) return;
        entityData.set(DATA_BEAM, Math.max(2.0F, groundDist));
        entityData.set(DATA_STATE, RELEASED);
        stateTicks = 0;
        if (level() instanceof ServerLevel sl) {
            Vec3 p = position();
            LegendFx.far(sl, LegendFx.dust(color(), 2.5F), p.x, p.y + GAP, p.z, 60, radius() * 0.6, GAP, radius() * 0.6, 0);
            LegendFx.far(sl, Vfx.endRod(), p.x, p.y, p.z, 30, radius() * 0.5, 1.0, radius() * 0.5, 0.05);
            LegendFx.sound(sl, p, SoundEvents.BEACON_POWER_SELECT, 3.0F, 0.6F);
        }
    }

    private void fizzle() {
        entityData.set(DATA_STATE, FIZZLE);
        stateTicks = 0;
        if (level() instanceof ServerLevel sl) {
            Vec3 p = position();
            sl.sendParticles(Vfx.smoke(), p.x, p.y, p.z, 30, radius() * 0.5, 1.0, radius() * 0.5, 0.02);
            LegendFx.sound(sl, p, SoundEvents.BEACON_DEACTIVATE, 2.0F, 1.2F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (state() == CHARGING && random.nextInt(2) == 0) {
                float r = radius() * (0.4F + random.nextFloat() * 0.6F);
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 p = position();
                level().addParticle(LegendFx.dust(color(), 1.2F), p.x + Math.cos(a) * r, p.y + random.nextInt(Math.max(1, layers())) * GAP,
                        p.z + Math.sin(a) * r, 0, -0.05, 0);
            }
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        stateTicks++;
        switch (state()) {
            case CHARGING -> {
                if (sl.getGameTime() > keepAliveUntil) {
                    fizzle();
                    return;
                }
                if (follow != null) {
                    Vec3 p = position().lerp(follow, 0.35);
                    setPos(p.x, p.y, p.z);
                }
                if (tickCount % 10 == 0) LegendFx.sound(sl, position(), SoundEvents.BEACON_AMBIENT, 2.5F, 0.5F + 0.5F * Math.min(1F, tickCount / (float) chargeTicks()));
            }
            case RELEASED -> {
                if (stateTicks >= RELEASE_TICKS) discard();
            }
            default -> {
                if (stateTicks >= FIZZLE_TICKS) discard();
            }
        }
        if (tickCount > 1200) discard();
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        float r = radius() + 1;
        Vec3 p = position();
        return new AABB(p.x - r, p.y - beam() - 1, p.z - r, p.x + r, p.y + layers() * GAP + 2, p.z + r);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
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
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
