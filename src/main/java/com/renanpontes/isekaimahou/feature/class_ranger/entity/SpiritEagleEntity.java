package com.renanpontes.isekaimahou.feature.class_ranger.entity;

import org.joml.Vector3f;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Águia Espiritual gigante da forma G "Tempestade das Mil Penas": só VISUAL (não é criatura). Circula sobre a área
 * enquanto as flechas caem e, no fim, mergulha no centro. A trajetória é calculada dos dados sincronizados
 * (centro, raio, duração), igual no servidor e no cliente — nada de pacote por tick. Some sozinha.
 */
public class SpiritEagleEntity extends Entity implements GeoEntity {
    public static final String ID = "ranger_spirit_eagle";
    private static final EntityDataAccessor<Vector3f> DATA_CENTER = SynchedEntityData.defineId(SpiritEagleEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(SpiritEagleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_CIRCLE = SynchedEntityData.defineId(SpiritEagleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(SpiritEagleEntity.class, EntityDataSerializers.INT);
    public static final int DIVE_TICKS = 10;
    private static final double HEIGHT = 14.0;
    private static final RawAnimation SOAR = RawAnimation.begin().thenLoop("animation.ranger_eagle.glide");
    private static final RawAnimation DIVE = RawAnimation.begin().thenLoop("animation.ranger_eagle.dive_hold");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SpiritEagleEntity(EntityType<? extends SpiritEagleEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** Configura antes do addFreshEntity: centro do mergulho, raio do voo, ticks circulando, cor RGB. */
    public void setup(Vec3 center, float radius, int circleTicks, int rgb) {
        entityData.set(DATA_CENTER, new Vector3f((float) center.x, (float) center.y, (float) center.z));
        entityData.set(DATA_RADIUS, radius);
        entityData.set(DATA_CIRCLE, circleTicks);
        entityData.set(DATA_COLOR, rgb);
        Vec3 p = pathAt(0);
        setPos(p.x, p.y, p.z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_CENTER, new Vector3f());
        builder.define(DATA_RADIUS, 6F);
        builder.define(DATA_CIRCLE, 60);
        builder.define(DATA_COLOR, 0xFFE27A);
    }

    public int color() {
        return entityData.get(DATA_COLOR);
    }

    public boolean diving() {
        return tickCount >= entityData.get(DATA_CIRCLE);
    }

    private Vec3 center() {
        Vector3f c = entityData.get(DATA_CENTER);
        return new Vec3(c.x(), c.y(), c.z());
    }

    /** Posição no tick t: círculo alto em volta do centro e, depois, mergulho em linha até o chão. */
    private Vec3 pathAt(float t) {
        Vec3 c = center();
        float r = entityData.get(DATA_RADIUS);
        int circle = entityData.get(DATA_CIRCLE);
        if (t <= circle) {
            double a = t * 0.11;
            return new Vec3(c.x + Math.cos(a) * r, c.y + HEIGHT, c.z + Math.sin(a) * r);
        }
        double a = circle * 0.11;
        Vec3 from = new Vec3(c.x + Math.cos(a) * r, c.y + HEIGHT, c.z + Math.sin(a) * r);
        float k = Mth.clamp((t - circle) / DIVE_TICKS, 0F, 1F);
        return from.lerp(c.add(0, 0.5, 0), k * k);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 now = pathAt(tickCount);
        Vec3 next = pathAt(tickCount + 1);
        Vec3 d = next.subtract(now);
        setPos(now.x, now.y, now.z);
        if (d.lengthSqr() > 1.0E-6) {
            setYRot((float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG));
            setXRot((float) (-Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG));
        }
        if (level() instanceof ServerLevel sl) {
            if (tickCount % 2 == 0) sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.5, getZ(), 2, 0.6, 0.2, 0.6, 0.01);
            if (tickCount > entityData.get(DATA_CIRCLE) + DIVE_TICKS + 2) discard();
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 160 * 160;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // efêmera: não volta de um save
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<SpiritEagleEntity>(this, "flight", 4, state -> state.setAndContinue(diving() ? DIVE : SOAR)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
