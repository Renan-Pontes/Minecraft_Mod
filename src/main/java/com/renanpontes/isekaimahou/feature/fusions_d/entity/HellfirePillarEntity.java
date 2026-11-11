package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Pilar de Chamas Negras: o chão racha num círculo vermelho e, depois do aviso, uma coluna de fogo negro do inferno
 * irrompe até o céu — arremessa quem está em cima, queima sem parar (Em Chamas acumulando) e calcina o chão.
 */
public class HellfirePillarEntity extends FdSpellEntity {
    public static final int CHARGE = 0, ERUPT = 1;
    public static final int CHARGE_TICKS = 16;
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(HellfirePillarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> HEIGHT = SynchedEntityData.defineId(HellfirePillarEntity.class, EntityDataSerializers.FLOAT);

    private float damage = 3F;

    public HellfirePillarEntity(EntityType<? extends HellfirePillarEntity> type, Level level) {
        super(type, level);
    }

    public static void raise(SpellContext ctx, Vec3 at, SpellStats stats) {
        ServerLevel sl = ctx.level();
        enforceCap(sl, FdRegistries.HELLFIRE_PILLAR.get(), ctx.casterId(), 8);
        Vec3 g = FdUtil.ground(sl, at, 8);
        HellfirePillarEntity p = new HellfirePillarEntity(FdRegistries.HELLFIRE_PILLAR.get(), sl);
        p.setPos(g.x, g.y, g.z);
        p.setup(ctx, CHARGE_TICKS + stats.duration(60), stats);
        p.damage = 3F * stats.power();
        float r = Math.min(7F, stats.radius(2.6F));
        p.entityData.set(RADIUS, r);
        p.entityData.set(HEIGHT, Math.min(40F, 16F + 4F * Math.max(0, stats.amplify)));
        sl.addFreshEntity(p);
        Telegraph.show(sl, Telegraph.Shape.circle(g, r).warmup(CHARGE_TICKS).color(FdFx.HELLFIRE));
        FdFx.sound(sl, g, SoundEvents.FIRECHARGE_USE, 1.4F, 0.5F);
        FdFx.sound(sl, g, SoundEvents.SOUL_ESCAPE.value(), 1.6F, 0.5F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(RADIUS, 2.6F);
        b.define(HEIGHT, 16F);
    }

    public float radius() { return entityData.get(RADIUS); }

    public float height() { return entityData.get(HEIGHT); }

    @Override
    protected void serverTick(ServerLevel sl) {
        Vec3 c = position();
        float r = radius();
        if (state() == CHARGE) {
            if (tickCount % 2 == 0) {
                FdFx.ring(sl, c.add(0, 0.1, 0), FdFx.fade(FdFx.HELLFIRE, FdFx.HELL_BLACK, 1.8F), r * (1 - tickCount / (float) CHARGE_TICKS), 10, 0.01);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.2, c.z, 4, r * 0.5, 0.05, r * 0.5, 0.01);
            }
            if (tickCount >= CHARGE_TICKS) erupt(sl, c, r);
            return;
        }
        if (tickCount % 5 == 0) {
            for (LivingEntity le : column(c, r)) {
                hit(le, damage, null);
                if (!FdUtil.isBoss(le) && le.getY() < c.y + height() * 0.6) FdUtil.setMotion(le, le.getDeltaMovement().multiply(0.6, 0, 0.6).add(0, 0.45, 0));
            }
        }
        if (tickCount % 3 == 0) {
            sl.sendParticles(FdFx.fade(FdFx.HELLFIRE, FdFx.HELL_BLACK, 1.8F), c.x, c.y + 0.5, c.z, 6, r * 0.6, 0.3, r * 0.6, 0.15);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + height(), c.z, 4, r * 0.6, 0.6, r * 0.6, 0.05);
        }
        if (tickCount % 20 == 0) FdFx.sound(sl, c, SoundEvents.BLAZE_BURN, 2F, 0.5F);
    }

    private java.util.List<LivingEntity> column(Vec3 c, float r) {
        java.util.List<LivingEntity> out = new java.util.ArrayList<>();
        for (LivingEntity le : foes(c.add(0, height() * 0.5, 0), Math.max(r, height() * 0.5) + 1)) {
            double dx = le.getX() - c.x, dz = le.getZ() - c.z;
            double rr = r + le.getBbWidth() * 0.5;
            if (dx * dx + dz * dz <= rr * rr && le.getY() >= c.y - 1 && le.getY() <= c.y + height()) out.add(le);
        }
        return out;
    }

    private void erupt(ServerLevel sl, Vec3 c, float r) {
        setState(ERUPT);
        for (LivingEntity le : column(c, r)) {
            hit(le, damage * 2.5F, null);
            Statuses.apply(le, Statuses.Kind.BURNING, 2, 160, owner(), 1F);
            if (!FdUtil.isBoss(le)) FdUtil.setMotion(le, new Vec3(0, 1.25, 0));
        }
        Destruction.cylinder(sl, c.add(0, -0.5, 0), r + 0.3F, -3F, owner(), Destruction.Mode.SCORCH);
        Destruction.surface(sl, c, r * 1.8F, owner(), Destruction.Mode.SCORCH);
        sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 1, c.z, 4, r * 0.4, 0.5, r * 0.4, 0);
        sl.sendParticles(ParticleTypes.FLAME, c.x, c.y + 1, c.z, 40, r * 0.4, 1.5, r * 0.4, 0.3);
        sl.sendParticles(FdFx.fade(FdFx.HELLFIRE, FdFx.HELL_BLACK, 1.8F), c.x, c.y + 1, c.z, 80, r * 0.4, 1.5, r * 0.4, 0.35);
        FdFx.shockwave(sl, c.add(0, 0.3, 0), FdFx.fade(FdFx.HELLFIRE, FdFx.HELL_BLACK, 2.2F), 36, 0.55);
        FdFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 2F, 0.6F);
        FdFx.sound(sl, c, SoundEvents.WITHER_SHOOT, 1.6F, 0.4F);
        FdFx.sound(sl, c, SoundEvents.GHAST_SHOOT, 1.4F, 0.5F);
        BossKit.shake(sl, c, 28, 1.4F, 18);
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        Vec3 c = position();
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 2, c.z, 40, radius() * 0.6, 2, radius() * 0.6, 0.05);
        sl.sendParticles(ParticleTypes.SOUL, c.x, c.y + 1, c.z, 12, radius() * 0.5, 1, radius() * 0.5, 0.05);
        FdFx.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 1.4F, 0.5F);
    }

    @Override
    protected void clientTick() {
        if (state() != ERUPT) return;
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = radius() * random.nextDouble();
            level().addParticle(i == 0 ? FdFx.fade(FdFx.HELLFIRE, FdFx.HELL_BLACK, 1.8F) : ParticleTypes.LARGE_SMOKE, getX() + Math.cos(a) * d,
                    getY() + random.nextDouble() * height(), getZ() + Math.sin(a) * d, 0, 0.25, 0);
        }
    }
}
