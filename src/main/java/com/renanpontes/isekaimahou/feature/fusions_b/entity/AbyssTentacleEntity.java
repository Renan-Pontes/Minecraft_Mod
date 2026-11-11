package com.renanpontes.isekaimahou.feature.fusions_b.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.fusions_b.FbFx;
import com.renanpontes.isekaimahou.feature.fusions_b.FbRegistries;
import com.renanpontes.isekaimahou.feature.fusions_b.FbUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Tentáculos do Abismo: brotam do chão (ou do fundo d'água) em volta do alvo, enrolam e puxam para baixo.
 * Dentro d'água puxam muito mais forte, arrastando para o fundo. Um único "ninho" desenha vários tentáculos.
 */
public class AbyssTentacleEntity extends FbSpellEntity {
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(AbyssTentacleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> COUNT = SynchedEntityData.defineId(AbyssTentacleEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> SPREAD = SynchedEntityData.defineId(AbyssTentacleEntity.class, EntityDataSerializers.FLOAT);

    private float damage = 1.5F;

    public AbyssTentacleEntity(EntityType<? extends AbyssTentacleEntity> type, Level level) {
        super(type, level);
    }

    public static void grab(SpellContext ctx, LivingEntity target, int count, int ticks, float damage, SpellStats stats) {
        ServerLevel sl = ctx.level();
        for (AbyssTentacleEntity t : sl.getEntities(FbRegistries.ABYSS_TENTACLE.get(), t -> t.isAlive() && t.targetId() == target.getId())) {
            t.extendLife(ticks);
            t.ctx = ctx;
            t.setHitStats(stats);
            t.damage = Math.max(t.damage, damage);
            return;
        }
        AbyssTentacleEntity t = new AbyssTentacleEntity(FbRegistries.ABYSS_TENTACLE.get(), sl);
        Vec3 at = FbUtil.ground(sl, target.position(), 6);
        t.setPos(at.x, at.y, at.z);
        t.setYRot(sl.random.nextFloat() * 360F);
        t.setup(ctx, ticks);
        t.setHitStats(stats);
        t.damage = damage;
        t.entityData.set(TARGET, target.getId());
        t.entityData.set(COUNT, (byte) Math.max(2, Math.min(6, count)));
        t.entityData.set(SPREAD, Math.max(0.6F, target.getBbWidth() * 0.5F + 0.55F));
        sl.addFreshEntity(t);
        sl.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.2, at.z, 20, 0.8, 0.1, 0.8, 0.05);
        sl.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 0.3, at.z, 16, 0.8, 0.3, 0.8, 0.05);
        FbFx.ring(sl, at.add(0, 0.1, 0), FbFx.dust(FbFx.ABYSS_DEEP, 1.6F), 1.4, 20, 0);
        FbFx.sound(sl, at, SoundEvents.SQUID_HURT, 1.2F, 0.5F);
        FbFx.sound(sl, at, SoundEvents.CONDUIT_ATTACK_TARGET, 0.9F, 0.6F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TARGET, -1);
        builder.define(COUNT, (byte) 4);
        builder.define(SPREAD, 0.9F);
    }

    public int targetId() { return entityData.get(TARGET); }
    public int count() { return entityData.get(COUNT); }
    public float spread() { return entityData.get(SPREAD); }

    @Nullable
    public Entity target() { return level().getEntity(targetId()); }

    @Override
    protected void serverTick(ServerLevel sl) {
        Entity t = target();
        if (!(t instanceof LivingEntity le) || !le.isAlive() || le.distanceToSqr(this) > 7 * 7) {
            end();
            return;
        }
        // Os tentáculos só agarram depois de terminar de brotar
        if (maxLife() - life < 6) return;
        boolean water = le.isInWater();
        Vec3 v = le.getDeltaMovement();
        Vec3 off = new Vec3(getX() - le.getX(), 0, getZ() - le.getZ());
        double down = water ? -0.25 : -0.12;
        Vec3 motion = new Vec3(off.x * 0.25, le.onGround() ? Math.min(0, v.y) : Math.min(v.y, down), off.z * 0.25);
        if (le instanceof ServerPlayer) FbUtil.setMotion(le, motion);
        else le.setDeltaMovement(motion);
        if (tickCount % 20 == 0) {
            hit(le, water ? damage * 1.5F : damage);
            sl.sendParticles(ParticleTypes.SQUID_INK, le.getX(), le.getY() + le.getBbHeight() * 0.4, le.getZ(), 6,
                    le.getBbWidth() * 0.4, le.getBbHeight() * 0.3, le.getBbWidth() * 0.4, 0.02);
            FbFx.sound(sl, le.position(), SoundEvents.SQUID_SQUIRT, 0.5F, 0.6F);
        }
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + 0.3, getZ(), 14, 0.7, 0.2, 0.7, 0.03);
        FbFx.sound(sl, position(), SoundEvents.SQUID_AMBIENT, 0.8F, 0.5F);
    }

    @Override
    protected void clientTick() {
        if (random.nextInt(3) == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = spread() * (0.6 + random.nextDouble() * 0.6);
            level().addParticle(isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.SQUID_INK,
                    getX() + Math.cos(a) * r, getY() + 0.1, getZ() + Math.sin(a) * r, 0, 0.04, 0);
        }
    }
}
