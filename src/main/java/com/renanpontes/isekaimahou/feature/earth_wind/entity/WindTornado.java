package com.renanpontes.isekaimahou.feature.earth_wind.entity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.earth_wind.EwFx;
import com.renanpontes.isekaimahou.feature.earth_wind.EwRegistries;
import com.renanpontes.isekaimahou.feature.earth_wind.EwUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Tornado (glifo Tornado): funil de vento que anda pelo chao perseguindo o inimigo mais proximo,
 * suga, gira e levanta quem estiver dentro, causando dano de Vento aos poucos. No fim arremessa todos.
 * Visual feito so de particulas (no cliente).
 */
public class WindTornado extends SpellBoundEntity {
    private static final double HEIGHT = 5.0;
    private UUID targetId;
    private Vec3 wander = Vec3.ZERO;
    /** Passo de movimento do ultimo tick (as vitimas sao arrastadas junto). */
    private Vec3 step = Vec3.ZERO;
    /** Quem esta preso no funil agora (para arremessar no fim). */
    private final Set<UUID> caught = new HashSet<>();

    public WindTornado(EntityType<? extends WindTornado> type, Level level) {
        super(type, level);
    }

    public static WindTornado spawn(SpellContext ctx, Vec3 pos, float radius, int life, float power) {
        ServerLevel level = ctx.level();
        limitPerOwner(level, WindTornado.class, ctx.casterId(), 2, 64, new AABB(pos, pos));
        WindTornado t = new WindTornado(EwRegistries.WIND_TORNADO.get(), level);
        t.bind(ctx, radius, life, power);
        t.moveTo(pos.x, pos.y, pos.z, 0, 0);
        level.addFreshEntity(t);
        level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, pos.x, pos.y + 1, pos.z, 1, 0, 0, 0, 0);
        EwFx.sound(level, pos, SoundEvents.BREEZE_WHIRL, 1.5F, 0.6F);
        EwFx.sound(level, pos, SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 0.6F);
        return t;
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) return;
        if (level().isClientSide) {
            clientFx();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        move(level);
        swirl(level);
        if (tickCount % 20 == 0) EwFx.sound(level, position(), SoundEvents.BREEZE_WHIRL, 1.0F, 0.5F + random.nextFloat() * 0.2F);
    }

    /** Persegue o inimigo mais proximo (ou vaga), seguindo o relevo. */
    private void move(ServerLevel level) {
        LivingEntity target = targetId != null && level.getEntity(targetId) instanceof LivingEntity le && isEnemy(le)
                && le.distanceToSqr(this) < 20 * 20 ? le : null;
        if (target == null && tickCount % 10 == 0) {
            double best = 16 * 16;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(16), this::isEnemy)) {
                double d = e.distanceToSqr(this);
                if (d < best) {
                    best = d;
                    target = e;
                }
            }
            targetId = target != null ? target.getUUID() : null;
        }
        Vec3 dir;
        if (target != null) {
            dir = EwUtil.horizontal(target.position().subtract(position()));
            if (dir.lengthSqr() > 1.0E-4) dir = dir.normalize();
        } else {
            if (tickCount % 30 == 0 || wander == Vec3.ZERO) {
                double a = random.nextDouble() * Math.PI * 2;
                wander = new Vec3(Math.cos(a), 0, Math.sin(a));
            }
            dir = wander.scale(0.4);
        }
        double speed = 0.16;
        step = dir.scale(speed);
        double nx = getX() + step.x;
        double nz = getZ() + step.z;
        // acompanha o chao: sobe degraus de ate 2 blocos e desce encostas
        BlockPos ground = EwUtil.groundBelow(level, BlockPos.containing(nx, getY() + 2, nz), 8);
        if (ground == null) {
            setPos(nx, getY() - 0.3, nz);
        } else {
            double ny = ground.getY() + 1.0;
            if (ny - getY() <= 2.1) setPos(nx, getY() + (ny - getY()) * 0.5, nz);
            else wander = wander.scale(-1);
        }
    }

    /** Suga, gira e levanta os inimigos dentro do funil. */
    private void swirl(ServerLevel level) {
        double r = radius();
        SpellContext c = context();
        AABB box = new AABB(getX() - r, getY() - 1, getZ() - r, getX() + r, getY() + HEIGHT + 1, getZ() + r);
        caught.clear();
        for (Entity e : level.getEntities(this, box, e -> e instanceof LivingEntity || e instanceof ItemEntity)) {
            if (e instanceof LivingEntity le && !isEnemy(le)) continue;
            Vec3 rel = EwUtil.horizontal(e.position().subtract(position()));
            double dist = rel.length();
            if (dist > r) continue;
            Vec3 radial = dist > 1.0E-3 ? rel.scale(1 / dist) : new Vec3(1, 0, 0);
            Vec3 tangent = new Vec3(-radial.z, 0, radial.x);
            double k = EwUtil.knockbackFactor(e);
            double height = e.getY() - getY();
            double lift = height < HEIGHT - 1 ? 0.22 : 0.02;
            Vec3 v = tangent.scale(0.45).add(radial.scale(dist > 0.8 ? -0.18 : 0.05)).scale(k);
            EwUtil.setVelocity(e, new Vec3(v.x + step.x, Math.max(e.getDeltaMovement().y * 0.5, lift * k), v.z + step.z));
            e.resetFallDistance();
            EwUtil.holdAloft(e);
            if (e instanceof LivingEntity le) {
                caught.add(le.getUUID());
                if (tickCount % 10 == 0) {
                    if (c != null) c.damage(le, 2.0F * power, new SpellStats(), this, Element.WIND);
                    else le.hurt(level.damageSources().flyIntoWall(), 2.0F * power);
                }
            }
        }
    }

    @Override
    protected void expire() {
        if (level() instanceof ServerLevel level) {
            // arremessa para fora quem ainda estava no funil
            for (UUID id : caught) {
                if (level.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
                    Vec3 out = EwUtil.horizontal(le.position().subtract(position()));
                    if (out.lengthSqr() < 1.0E-4) out = new Vec3(1, 0, 0);
                    EwUtil.push(le, out.normalize().scale(1.1), 0.5);
                }
            }
            EwFx.windSpiral(level, position(), radius(), HEIGHT, 40, tickCount * 0.4);
            level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
            EwFx.sound(level, position(), SoundEvents.WIND_CHARGE_BURST.value(), 1.2F, 0.7F);
        }
        discard();
    }

    // =====================================================================================
    //  Visual (cliente)
    // =====================================================================================

    private void clientFx() {
        Level level = level();
        boolean reduced = MahouConfig.REDUCED_PARTICLES.get();
        int n = reduced ? 6 : 16;
        double r = radius();
        int left = life() - tickCount;
        double fade = left < 20 ? Math.max(0.2, left / 20.0) : 1.0;
        double spin = tickCount * 0.45;
        for (int i = 0; i < n * fade; i++) {
            double h = random.nextDouble() * HEIGHT;
            double rr = r * (0.25 + 0.75 * h / HEIGHT) * (0.85 + random.nextDouble() * 0.3);
            double a = spin + random.nextDouble() * Math.PI * 2;
            double x = getX() + Math.cos(a) * rr;
            double z = getZ() + Math.sin(a) * rr;
            double vx = -Math.sin(a) * 0.25;
            double vz = Math.cos(a) * 0.25;
            level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.POOF : ParticleTypes.CLOUD, x, getY() + h, z, vx, 0.04, vz);
            if (i % 4 == 0) level.addParticle(ElementFx.dust(Element.WIND, 1.3F), x, getY() + h, z, vx, 0.02, vz);
        }
        // poeira do chao girando na base
        if (random.nextInt(2) == 0) {
            BlockState below = level.getBlockState(blockPosition().below());
            if (!below.isAir()) {
                double a = random.nextDouble() * Math.PI * 2;
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, below), getX() + Math.cos(a) * r * 0.5, getY() + 0.1,
                        getZ() + Math.sin(a) * r * 0.5, -Math.sin(a) * 0.3, 0.3, Math.cos(a) * 0.3);
            }
        }
    }
}
