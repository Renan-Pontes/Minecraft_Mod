package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Pilar do Ragnarök (só visual; o golpe é da telegrafia): coluna de luz dourada ou de sombra violeta que desce do céu
 * e explode no chão, encolhendo até sumir.
 */
public class TwilightPillarEntity extends FdSpellEntity {
    private static final EntityDataAccessor<Boolean> SHADOW = SynchedEntityData.defineId(TwilightPillarEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(TwilightPillarEntity.class, EntityDataSerializers.FLOAT);
    public static final int LIFE = 26;

    public TwilightPillarEntity(EntityType<? extends TwilightPillarEntity> type, Level level) {
        super(type, level);
    }

    public static void spawn(ServerLevel sl, Vec3 ground, float radius, boolean shadow) {
        TwilightPillarEntity p = new TwilightPillarEntity(FdRegistries.TWILIGHT_PILLAR.get(), sl);
        p.setPos(ground.x, ground.y, ground.z);
        p.setup(null, LIFE, new com.renanpontes.isekaimahou.glyph.SpellStats());
        p.entityData.set(SHADOW, shadow);
        p.entityData.set(RADIUS, radius);
        sl.addFreshEntity(p);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(SHADOW, false);
        b.define(RADIUS, 2F);
    }

    public boolean shadow() { return entityData.get(SHADOW); }

    public float radius() { return entityData.get(RADIUS); }

    @Override
    protected void serverTick(ServerLevel level) {}

    @Override
    protected void clientTick() {
        if (tickCount > 10) return;
        for (int i = 0; i < 4; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = radius() * random.nextDouble();
            level().addParticle(shadow() ? Vfx.ink() : Vfx.endRod(), getX() + Math.cos(a) * d,
                    getY() + 0.2, getZ() + Math.sin(a) * d, Math.cos(a) * 0.2, 0.25, Math.sin(a) * 0.2);
        }
    }
}
