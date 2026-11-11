package com.renanpontes.isekaimahou.feature.class_smith.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.SmithRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Fagulha da Chuva de Fagulhas: projétil pequeno e incandescente, com gravidade leve, que aplica os efeitos da magia no
 * primeiro alvo (ou bloco) que tocar. Vive 1,5 s, não é salvo no mundo e some sozinho (o contexto da magia só existe
 * na memória). O rastro é desenhado no cliente (partículas) e o corpo pelo renderer emissivo.
 */
public class SmithSparkEntity extends Projectile {
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(SmithSparkEntity.class, EntityDataSerializers.INT);
    public static final int LIFE = 30;

    @Nullable private SpellContext ctx;
    private final Set<UUID> hit = new HashSet<>();

    public SmithSparkEntity(EntityType<? extends SmithSparkEntity> type, Level level) {
        super(type, level);
    }

    public static SmithSparkEntity create(ServerLevel level, SpellContext ctx, Vec3 from, Vec3 dir, float speed, int color) {
        SmithSparkEntity e = new SmithSparkEntity(SmithRegistries.SPARK.get(), level);
        e.ctx = ctx;
        e.setOwner(ctx.caster());
        e.setPos(from.x, from.y, from.z);
        Vec3 v = dir.normalize().scale(speed);
        e.setDeltaMovement(v);
        e.entityData.set(DATA_COLOR, color & 0xFFFFFF);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR, SmithFx.MOLTEN);
    }

    public int color() {
        return entityData.get(DATA_COLOR);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 pos = position();
        if (!level().isClientSide) {
            if (ctx == null || tickCount > LIFE) {
                if (ctx != null) ctx.end(pos, getDeltaMovement());
                discard();
                return;
            }
            HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hitResult.getType() != HitResult.Type.MISS) {
                onHit(hitResult);
                if (isRemoved()) return;
            }
        } else {
            // rastro de brasa (só visual)
            level().addParticle(SmithFx.spark(color()), pos.x, pos.y, pos.z, 0, 0, 0);
            if (random.nextInt(4) == 0) level().addParticle(ParticleTypes.SMALL_FLAME, pos.x, pos.y, pos.z, 0, 0.01, 0);
        }
        Vec3 v = getDeltaMovement();
        setPos(pos.x + v.x, pos.y + v.y, pos.z + v.z);
        setDeltaMovement(v.scale(0.97).add(0, -0.025, 0));
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target) || hit.contains(target.getUUID())) return false;
        return target instanceof LivingEntity && target.isAlive() && (ctx == null || target != ctx.caster());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        hit.add(target.getUUID());
        if (ctx != null) {
            ctx.resolveOnEntity(target, this);
            ctx.impact(result.getLocation(), getDeltaMovement(), target);
        }
        burst();
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (ctx != null) {
            ctx.resolveOnBlock(result, this);
            ctx.impact(result.getLocation(), getDeltaMovement(), null);
        }
        burst();
        discard();
    }

    private void burst() {
        if (level() instanceof ServerLevel sl) {
            Vec3 p = position();
            sl.sendParticles(SmithFx.spark(color()), p.x, p.y, p.z, 6, 0.1, 0.1, 0.1, 0.08);
            sl.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            if (random.nextInt(3) == 0) SmithFx.sound(sl, p, SoundEvents.LAVA_POP, 0.35F, 1.4F);
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
