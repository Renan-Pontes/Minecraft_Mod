package com.renanpontes.isekaimahou.feature.legends_c.entity;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.LcRegistry;
import com.renanpontes.isekaimahou.feature.legends_c.LcUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Vermelho (Inversão): um ponto de REPULSÃO. Disparado (Si Mesmo) voa rápido em linha reta empurrando o que passa ao
 * lado e explode no primeiro alvo/bloco; posto num alvo (projétil, toque...) detona ali em 6 ticks. A explosão é uma
 * onda vermelha esférica que arremessa tudo para longe, dano alto e uma cratera pequena.
 */
public class RedOrbEntity extends LcEntity {
    private static final EntityDataAccessor<Long> DATA_BURST = SynchedEntityData.defineId(RedOrbEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(RedOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(RedOrbEntity.class, EntityDataSerializers.FLOAT);
    public static final double SPEED = 1.8;
    public static final int FUSE = 6;
    public static final int BURST_TICKS = 18;
    public static final float DAMAGE = 16F;

    @Nullable private SpellStats stats;
    private Vec3 velocity = Vec3.ZERO;
    private boolean placed;
    private double travelled;
    private double maxDistance = 32;
    /** Já bateu numa barreira de domínio no voo (a explosão não tira integridade de novo). */
    private boolean wallHit;

    public RedOrbEntity(EntityType<? extends RedOrbEntity> type, Level level) {
        super(type, level);
    }

    private static RedOrbEntity create(SpellContext ctx, SpellStats stats, Vec3 at) {
        RedOrbEntity r = new RedOrbEntity(LcRegistry.RED_ORB.get(), ctx.level());
        r.ctx = ctx;
        r.stats = stats;
        r.bindOwner(ctx.caster());
        boolean colossal = LcUtil.colossal(stats);
        r.entityData.set(DATA_RADIUS, stats.radius(5F) * (colossal ? 2F : 1F));
        r.entityData.set(DATA_SIZE, 0.45F + 0.12F * Math.max(0, stats.amplify) + (colossal ? 0.6F : 0F));
        r.setPos(at.x, at.y, at.z);
        return r;
    }

    /** Dispara o Vermelho na direção {@code dir}. */
    public static void fire(SpellContext ctx, SpellStats stats, Vec3 from, Vec3 dir) {
        RedOrbEntity r = create(ctx, stats, from);
        r.velocity = dir.normalize().scale(SPEED * stats.speedMult());
        r.maxDistance = stats.radius(32F);
        r.setDeltaMovement(r.velocity);
        ctx.level().addFreshEntity(r);
        LcFx.sound(ctx.level(), from, SoundEvents.FIRECHARGE_USE, 1.4F, 0.6F);
        LcFx.sound(ctx.level(), from, SoundEvents.BEACON_DEACTIVATE, 1.4F, 1.6F);
    }

    /** Põe o Vermelho parado em {@code at}: detona em {@link #FUSE} ticks. */
    public static void place(SpellContext ctx, SpellStats stats, Vec3 at) {
        RedOrbEntity r = create(ctx, stats, at);
        r.placed = true;
        ctx.level().addFreshEntity(r);
        LcFx.sound(ctx.level(), at, SoundEvents.BEACON_DEACTIVATE, 1.4F, 1.4F);
    }

    @Override
    protected void defineExtra(SynchedEntityData.Builder builder) {
        builder.define(DATA_BURST, -1L);
        builder.define(DATA_RADIUS, 5F);
        builder.define(DATA_SIZE, 0.45F);
    }

    /** Ticks desde a explosão (-1 = ainda não explodiu); pelo relógio do mundo. */
    public int burstAge() {
        long b = entityData.get(DATA_BURST);
        return b < 0 ? -1 : (int) (level().getGameTime() - b);
    }
    public float radius() { return entityData.get(DATA_RADIUS); }
    public float size() { return entityData.get(DATA_SIZE); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (burstAge() < 0) {
                level().addParticle(LcFx.dust(LcFx.RED, 1.2F), getX() + (random.nextDouble() - 0.5) * 0.4,
                        getY() + (random.nextDouble() - 0.5) * 0.4, getZ() + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
            }
            return;
        }
        ServerLevel l = (ServerLevel) level();
        int burst = burstAge();
        if (burst >= 0) {
            if (burst >= BURST_TICKS) discard();
            return;
        }
        if (ctx == null || stats == null) {
            discard();
            return;
        }
        if (placed) {
            if (tickCount >= FUSE) burst(l, position());
            return;
        }
        fly(l);
    }

    private void fly(ServerLevel l) {
        Vec3 from = position();
        Vec3 to = from.add(velocity);
        LcUtil.Wall wall = LcUtil.firstWall(l, from, to, owner);
        if (wall != null) {
            wallHit = true;
            LcUtil.strikeWall(l, wall, owner != null ? owner.position() : from, owner, DAMAGE * 1.5F * stats.power() * ctx.powerMult());
            burst(l, wall.point().subtract(velocity.normalize().scale(0.4)));   // explode do lado de cá da parede
            return;
        }
        BlockHitResult bh = l.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 end = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation() : to;
        // Primeira criatura inimiga no caminho
        LivingEntity hit = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(1.2),
                e -> e.isAlive() && !e.isSpectator() && e != owner && !ctx.isAlly(e))) {
            var clip = e.getBoundingBox().inflate(0.5).clip(from, end);
            if (clip.isPresent()) {
                double d = clip.get().distanceToSqr(from);
                if (d < best) { best = d; hit = e; }
            }
        }
        if (hit != null) {
            burst(l, LcUtil.center(hit));
            return;
        }
        if (bh.getType() == HitResult.Type.BLOCK) {
            burst(l, bh.getLocation().subtract(velocity.normalize().scale(0.3)));
            return;
        }
        setPos(to.x, to.y, to.z);
        setDeltaMovement(velocity);
        travelled += velocity.length();
        // Rastro de repulsão: quem está ao lado é empurrado para fora da linha
        if (tickCount % 2 == 0) {
            for (LivingEntity e : ctx.enemiesAround(to, 2.5)) {
                Vec3 off = LcUtil.center(e).subtract(to);
                Vec3 side = off.subtract(velocity.normalize().scale(off.dot(velocity.normalize())));
                if (side.lengthSqr() < 1.0E-4) continue;
                e.push(side.normalize().x * 0.6, 0.15, side.normalize().z * 0.6);
                e.hurtMarked = true;
            }
        }
        if (travelled >= maxDistance) burst(l, to);
    }

    private void burst(ServerLevel l, Vec3 at) {
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        entityData.set(DATA_BURST, level().getGameTime());
        float r = radius();
        boolean colossal = LcUtil.colossal(stats);
        for (LivingEntity e : ctx.enemiesAround(at, r + 1.0)) {
            Vec3 n = LcUtil.center(e).subtract(at);
            double d = n.length();
            if (d > r + e.getBbWidth()) continue;
            if (LcUtil.behindWall(l, at, e, owner)) continue;   // a onda não atravessa a barreira de outro domínio
            float fall = (float) Mth.clamp(1.0 - 0.5 * d / r, 0.5, 1.0);
            ctx.damage(e, DAMAGE * fall * (colossal ? 1.5F : 1F), stats, this);
            n = d < 1.0E-3 ? new Vec3(0, 1, 0) : n.normalize();
            double s = 2.4 * Mth.clamp(1.0 - d / r, 0.0, 1.0) + 0.9;
            if (BossKit.isBossLike(e)) s *= 0.3;
            e.setDeltaMovement(n.scale(s).add(0, 0.45, 0));
            e.hurtMarked = true;
        }
        if (!wallHit && Domains.barrierAt(l, at) != null) {
            Domains.hitBarrier(l, at, owner != null ? owner.position() : at, owner, DAMAGE * 1.5F * stats.power() * ctx.powerMult());
        }
        float crater = (2.5F + 1.2F * Math.max(0, stats.aoe)) * (colossal ? 2F : 1F);
        Destruction.crater(l, at, Math.min(16F, crater), owner);
        LcFx.wave(l, at, new Vec3(0, 1, 0), r * 1.5F, LcFx.RED, 16);
        LcFx.wave(l, at, owner != null ? at.subtract(owner.getEyePosition()) : new Vec3(0, 0, 1), r * 2.2F, LcFx.RED_DEEP, 20);
        LcFx.flash(l, at, 3F + r * 0.6F, LcFx.RED, 10);
        LcFx.far(l, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        LcFx.far(l, LcFx.dust(LcFx.RED, 2.5F), at.x, at.y, at.z, 80, r * 0.6, r * 0.4, r * 0.6, 0.1);
        LcFx.sound(l, at, SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.8F);
        LcFx.sound(l, at, SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 1.5F);
        BossKit.shake(l, at, r * 6 + 16, colossal ? 2.2F : 1.3F, 14);
    }
}
