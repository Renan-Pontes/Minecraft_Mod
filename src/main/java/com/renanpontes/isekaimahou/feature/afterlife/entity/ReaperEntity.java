package com.renanpontes.isekaimahou.feature.afterlife.entity;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.afterlife.AfterlifeContent;
import com.renanpontes.isekaimahou.feature.afterlife.AfterlifeManager;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Ceifador: caçador de almas que aparece durante o desafio "À Beira da Morte". Lento, mas não para nunca — atravessa
 * paredes e acelera aos poucos. Encostar na alma = ela é levada (morte de verdade). Não é salvo, não leva dano e some
 * assim que o desafio do dono acaba.
 */
public class ReaperEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_TARGET = SynchedEntityData.defineId(ReaperEntity.class,
            EntityDataSerializers.OPTIONAL_UUID);
    /** Velocidade inicial (blocos/tique) e ganho por segundo. A alma anda ~3 blocos/s; eles começam em ~1,7. */
    private static final double BASE_SPEED = 0.085;
    private static final double SPEED_PER_SECOND = 0.0035;

    private int life;

    public ReaperEntity(EntityType<? extends ReaperEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static ReaperEntity spawn(ServerLevel level, ServerPlayer target, Vec3 at) {
        ReaperEntity r = new ReaperEntity(AfterlifeContent.REAPER.get(), level);
        r.moveTo(at.x, at.y, at.z, 0F, 0F);
        r.entityData.set(DATA_TARGET, Optional.of(target.getUUID()));
        r.face(target.position());
        level.addFreshEntity(r);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 1.0, at.z, 20, 0.3, 0.8, 0.3, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 12, 0.4, 0.4, 0.4, 0.01);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.7F, 1.4F);
        return r;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(DATA_TARGET, Optional.empty());
    }

    @Nullable
    public UUID target() {
        return entityData.get(DATA_TARGET).orElse(null);
    }

    @Override
    public void tick() {
        super.tick();
        life++;
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.SMOKE, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + random.nextDouble() * 0.6,
                        getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0.01, 0);
            }
            if (random.nextInt(8) == 0) {
                level().addParticle(ParticleTypes.SOUL, getX(), getY() + 1.9, getZ(), 0, 0.02, 0);
            }
            return;
        }
        UUID id = target();
        ServerPlayer target = id == null || getServer() == null ? null : getServer().getPlayerList().getPlayer(id);
        if (target == null || target.level() != level() || !AfterlifeManager.isHunting(target, this)) {
            discard();
            return;
        }
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.35, 0);
        Vec3 to = aim.subtract(position().add(0, getBbHeight() * 0.35, 0));
        double dist = to.length();
        double speed = BASE_SPEED + SPEED_PER_SECOND * (life / 20.0);
        Vec3 step = dist < 1.0E-4 ? Vec3.ZERO : to.scale(Math.min(speed, dist) / dist);
        setDeltaMovement(step);
        move(MoverType.SELF, step);
        face(aim);
        if (getBoundingBox().inflate(0.15).intersects(target.getBoundingBox())) {
            AfterlifeManager.onCaught(target, this);
            return;
        }
        if (life % 40 == 0) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.9F, 0.6F);
        }
    }

    private void face(Vec3 at) {
        double dx = at.x - getX(), dz = at.z - getZ();
        if (dx * dx + dz * dz < 1.0E-4) return;
        float yaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        setYRot(yaw);
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
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
