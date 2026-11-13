package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Equilíbrio Absoluto: um orbe duplo (yin-yang) gira no ar. A cada segundo pulsa: cura os aliados e tira deles uma
 * condição ruim; fere os inimigos alternando LUZ e SOMBRA (o que provoca Ruptura). Em Si Mesmo, flutua sobre você e
 * te acompanha.
 */
public class YinYangEntity extends FdSpellEntity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(YinYangEntity.class, EntityDataSerializers.FLOAT);
    private static final Statuses.Kind[] BAD = {Statuses.Kind.FROZEN, Statuses.Kind.PETRIFIED, Statuses.Kind.STUNNED, Statuses.Kind.SILENCED,
            Statuses.Kind.BURNING, Statuses.Kind.BLEEDING, Statuses.Kind.CORRUPTED, Statuses.Kind.SHOCKED, Statuses.Kind.CHILLED,
            Statuses.Kind.EXPOSED, Statuses.Kind.ROOTED, Statuses.Kind.WET};

    private float heal = 2F;
    private float damage = 3F;
    private boolean follow;
    private int pulses;
    private Vec3 lerpTarget;
    private int lerpSteps;

    public YinYangEntity(EntityType<? extends YinYangEntity> type, Level level) {
        super(type, level);
    }

    public static void summon(SpellContext ctx, Vec3 at, boolean followCaster, SpellStats stats) {
        ServerLevel sl = ctx.level();
        enforceCap(sl, FdRegistries.YIN_YANG.get(), ctx.casterId(), 1);
        YinYangEntity y = new YinYangEntity(FdRegistries.YIN_YANG.get(), sl);
        Vec3 p = followCaster && ctx.caster() != null ? ctx.caster().position().add(0, ctx.caster().getBbHeight() + 1.2, 0)
                : FdUtil.ground(sl, at, 6).add(0, 2.2, 0);
        y.setPos(p.x, p.y, p.z);
        y.setup(ctx, stats.duration(180), stats);
        y.follow = followCaster;
        y.heal = 2.0F * FdUtil.power(ctx, stats);
        y.damage = 3.0F * stats.power();
        y.entityData.set(RADIUS, Math.min(14F, stats.radius(6.5F)));
        sl.addFreshEntity(y);
        FdFx.sound(sl, p, SoundEvents.BEACON_ACTIVATE, 1.2F, 1.2F);
        FdFx.sound(sl, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.7F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(RADIUS, 6.5F);
    }

    public float radius() { return entityData.get(RADIUS); }

    @Override
    protected void serverTick(ServerLevel sl) {
        LivingEntity o = owner();
        if (follow && o != null && o.isAlive() && o.level() == sl) {
            Vec3 want = o.position().add(0, o.getBbHeight() + 1.2, 0);
            setPos(position().lerp(want, 0.35));
        }
        if (tickCount % 20 != 10) return;
        Vec3 c = position();
        float r = radius();
        boolean light = (pulses++ & 1) == 0;
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> e.isAlive() && !e.isSpectator() && e.distanceToSqr(c) <= r * r)) {
            if (ally(le)) {
                if (le.getHealth() < le.getMaxHealth()) le.heal(heal);
                for (Statuses.Kind k : BAD) {
                    if (Statuses.has(le, k)) {
                        Statuses.clear(le, k);
                        break;
                    }
                }
                FdFx.line(sl, c, FdUtil.center(le), FdFx.dust(0xFFF4C8, 0.9F), 2);
                sl.sendParticles(ParticleTypes.HEART, le.getX(), le.getY() + le.getBbHeight() + 0.3, le.getZ(), 1, 0.2, 0.1, 0.2, 0);
            } else if (!FdUtil.untouchable(le)) {
                hit(le, damage, light ? Element.LIGHT : Element.SHADOW);
                FdFx.line(sl, c, FdUtil.center(le), FdFx.dust(light ? FdFx.TWILIGHT_GOLD : FdFx.TWILIGHT_SHADOW, 1.1F), 3);
            }
        }
        FdFx.shockwave(sl, c, FdFx.fade(light ? FdFx.TWILIGHT_GOLD : FdFx.TWILIGHT_SHADOW, light ? FdFx.TWILIGHT_SHADOW : FdFx.TWILIGHT_GOLD, 1.5F),
                28, 0.35);
        FdFx.sound(sl, c, light ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.SCULK_CLICKING, 1.4F, light ? 1.4F : 0.6F);
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        Vec3 c = position();
        sl.sendParticles(Vfx.endRod(), c.x, c.y, c.z, 20, 0.2, 0.2, 0.2, 0.2);
        sl.sendParticles(Vfx.ink(), c.x, c.y, c.z, 20, 0.2, 0.2, 0.2, 0.2);
        FdFx.sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.2F);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        // Interpolação suave ao seguir o dono (o padrão de Entity teleporta direto)
        lerpTarget = new Vec3(x, y, z);
        lerpSteps = 3;
    }

    @Override
    protected void clientTick() {
        if (lerpSteps > 0 && lerpTarget != null) {
            setPos(position().add(lerpTarget.subtract(position()).scale(1.0 / lerpSteps)));
            lerpSteps--;
        }
    }
}
