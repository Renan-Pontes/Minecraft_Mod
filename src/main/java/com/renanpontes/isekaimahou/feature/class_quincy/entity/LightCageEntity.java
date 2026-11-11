package com.renanpontes.isekaimahou.feature.class_quincy.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyFx;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Sankt Zwinger (a Gaiola Sagrada): cinco pilares de luz na estrela Quincy erguem uma gaiola cilíndrica de grades de
 * Reishi. Inimigos lá dentro NÃO saem (são empurrados de volta para o centro; chefes ficam mais lentos) e, a cada
 * meio segundo, lâminas de luz RETALHAM todos lá dentro (os efeitos da magia em cada um). Dura o tempo da forma e
 * nunca é salva (some ao descarregar). A grade e a estrela no chão são desenhadas pelo cliente.
 */
public class LightCageEntity extends Entity {
    public static final int PULSE = 10;
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(LightCageEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(LightCageEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(LightCageEntity.class, EntityDataSerializers.INT);

    @Nullable
    private SpellContext ctx;

    public LightCageEntity(EntityType<? extends LightCageEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    public static LightCageEntity of(SpellContext ctx, Vec3 at, float radius, float height, int life) {
        LightCageEntity c = new LightCageEntity(QuincyRegistries.LIGHT_CAGE.get(), ctx.level());
        c.setPos(at.x, at.y, at.z);
        c.ctx = ctx;
        c.entityData.set(RADIUS, Math.max(2F, radius));
        c.entityData.set(HEIGHT, Math.max(3F, height));
        c.entityData.set(LIFE, Math.max(20, life));
        return c;
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public float height() {
        return entityData.get(HEIGHT);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(RADIUS, 5F);
        builder.define(HEIGHT, 6F);
        builder.define(LIFE, 120);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 3 == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                double r = radius();
                level().addParticle(ParticleTypes.END_ROD, getX() + Math.cos(a) * r, getY() + random.nextDouble() * height(),
                        getZ() + Math.sin(a) * r, 0, 0.02, 0);
            }
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (ctx == null || tickCount > life()) {
            if (ctx != null) close(sl);
            discard();
            return;
        }
        float r = radius();
        Vec3 c = position();
        // ninguém sai: quem chega na grade volta para dentro
        for (LivingEntity e : ctx.enemiesAround(c.add(0, height() * 0.5, 0), r + Math.max(2.0, height()))) {
            if (e.getY() < c.y - 1 || e.getY() > c.y + height()) continue;
            Vec3 flat = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
            double d = flat.length();
            if (d > r + 1.5) continue;
            if (d > r - 0.7) {
                Vec3 in = d < 1.0E-3 ? Vec3.ZERO : flat.normalize().scale(-(Statuses.isBossLike(e) ? 0.25 : 0.55));
                e.setDeltaMovement(in.x, Math.min(0.1, e.getDeltaMovement().y), in.z);
                e.hurtMarked = true;
                if (tickCount % 4 == 0) {
                    Vec3 at = c.add(flat.normalize().scale(r)).add(0, e.getBbHeight() * 0.5, 0);
                    sl.sendParticles(QuincyFx.WHITE, at.x, e.getY() + 1, at.z, 4, 0.1, 0.4, 0.1, 0.02);
                }
            }
            if (tickCount % PULSE == 0 && d <= r + 0.3) slash(sl, e);
        }
        if (tickCount % PULSE == 0) QuincyFx.sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.7F);
        if (tickCount % 20 == 0) QuincyFx.pentacle(sl, c.add(0, 0.08, 0), r, tickCount * 0.02F);
    }

    /** Lâminas de luz retalham quem está dentro: um X de luz no alvo e os efeitos da magia. */
    private void slash(ServerLevel sl, LivingEntity e) {
        Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
        for (int k = 0; k < 2; k++) {
            double a = random.nextDouble() * Mth.TWO_PI;
            Vec3 off = new Vec3(Math.cos(a), (random.nextDouble() - 0.5) * 1.4, Math.sin(a)).scale(0.9);
            QuincyFx.line(sl, at.add(off), at.subtract(off), QuincyFx.WHITE, 0.2);
        }
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.2, 0.2, 0.2, 0);
        ctx.impact(at, Vec3.ZERO, e);
        ctx.resolveOnEntity(e, this);
    }

    private void close(ServerLevel sl) {
        Vec3 c = position().add(0, height() * 0.5, 0);
        QuincyFx.burst(sl, c, 40, radius() * 0.5);
        QuincyFx.sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.4F);
        ctx.end(c, Vec3.ZERO);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 96 * 96;
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
