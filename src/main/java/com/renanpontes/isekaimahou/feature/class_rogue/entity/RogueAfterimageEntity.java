package com.renanpontes.isekaimahou.feature.class_rogue.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_rogue.RogueContent;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFxNet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Imagem residual do Ladino (Ritmo de Eco, Velocidade Divina): uma cópia translúcida do corpo de quem a deixou
 * (desenhada com a pele do jogador, tingida), parada ou girando em volta de um alvo. Só visual; não é salva, não pode
 * ser atingida e some sozinha em poucos segundos (desbota no fim).
 */
public class RogueAfterimageEntity extends Entity {
    public static final byte POSE_STAND = 0;
    public static final byte POSE_DASH = 1;
    public static final byte POSE_STRIKE = 2;

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER = SynchedEntityData.defineId(RogueAfterimageEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(RogueAfterimageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(RogueAfterimageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_POSE = SynchedEntityData.defineId(RogueAfterimageEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DATA_SPARK = SynchedEntityData.defineId(RogueAfterimageEntity.class, EntityDataSerializers.BOOLEAN);

    // órbita (servidor)
    @Nullable private LivingEntity orbitTarget;
    private double orbitRadius;
    private double angle;
    private double angSpeed;

    public RogueAfterimageEntity(EntityType<? extends RogueAfterimageEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Imagem parada em {@code at}, virada para {@code yaw}, por {@code life} ticks. */
    public static RogueAfterimageEntity still(ServerLevel level, @Nullable LivingEntity owner, Vec3 at, float yaw, int life, int color,
                                              byte pose, boolean spark) {
        RogueAfterimageEntity e = new RogueAfterimageEntity(RogueContent.AFTERIMAGE.get(), level);
        e.setPos(at.x, at.y, at.z);
        e.setYRot(yaw);
        e.yRotO = yaw;
        if (owner != null) e.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
        e.entityData.set(DATA_COLOR, color & 0xFFFFFF);
        e.entityData.set(DATA_LIFE, life);
        e.entityData.set(DATA_POSE, pose);
        e.entityData.set(DATA_SPARK, spark);
        return e;
    }

    /** Imagem que GIRA em volta de {@code target} (raio, ângulo inicial e velocidade em rad/tique). */
    public static RogueAfterimageEntity orbit(ServerLevel level, @Nullable LivingEntity owner, LivingEntity target, double radius,
                                              double angle, double speed, int life, int color, boolean spark) {
        Vec3 at = target.position().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        RogueAfterimageEntity e = still(level, owner, at, 0F, life, color, POSE_DASH, spark);
        e.orbitTarget = target;
        e.orbitRadius = radius;
        e.angle = angle;
        e.angSpeed = speed;
        e.faceTarget();
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_COLOR, RogueFx.PURPLE);
        builder.define(DATA_LIFE, 30);
        builder.define(DATA_POSE, POSE_STAND);
        builder.define(DATA_SPARK, false);
    }

    @Nullable
    public UUID ownerId() { return entityData.get(DATA_OWNER).orElse(null); }
    public int color() { return entityData.get(DATA_COLOR); }
    public int life() { return entityData.get(DATA_LIFE); }
    public byte pose() { return entityData.get(DATA_POSE); }
    public boolean spark() { return entityData.get(DATA_SPARK); }

    public void setPose(byte pose) {
        entityData.set(DATA_POSE, pose);
    }

    /** O alvo da órbita (para o ataque final das imagens). */
    @Nullable
    public LivingEntity orbitTarget() {
        return orbitTarget;
    }

    private void faceTarget() {
        if (orbitTarget == null) return;
        Vec3 d = orbitTarget.position().subtract(position());
        float yaw = (float) (Math.atan2(-d.x, d.z) * (180F / Math.PI));
        setYRot(yaw);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (spark() && random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + (random.nextDouble() - 0.5) * 0.6,
                        getY() + random.nextDouble() * 1.8, getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0, 0);
            } else if (random.nextInt(4) == 0) {
                level().addParticle(RogueFx.dust(color(), 0.7F), getX() + (random.nextDouble() - 0.5) * 0.5,
                        getY() + random.nextDouble() * 1.8, getZ() + (random.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
            }
            return;
        }
        if (tickCount > life()) {
            discard();
            return;
        }
        if (orbitTarget != null) {
            if (!orbitTarget.isAlive() || orbitTarget.level() != level()) {
                orbitTarget = null;
                return;
            }
            angle += angSpeed;
            Vec3 at = orbitTarget.position().add(Math.cos(angle) * orbitRadius, 0, Math.sin(angle) * orbitRadius);
            setPos(at.x, at.y, at.z);
            faceTarget();
        }
    }

    /** A imagem avança no alvo e some (o golpe é aplicado por quem a criou). */
    public void lunge() {
        if (orbitTarget != null && level() instanceof ServerLevel sl) {
            Vec3 from = position().add(0, 1, 0);
            Vec3 to = RogueFx.center(orbitTarget);
            RogueFxNet.at(sl, RogueFxNet.LUNGE, from, to, 1F, color(), getId());
        }
        setPose(POSE_STRIKE);
        orbitTarget = null;
        entityData.set(DATA_LIFE, Math.min(life(), tickCount + 6));
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
        return d < 64 * 64;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
