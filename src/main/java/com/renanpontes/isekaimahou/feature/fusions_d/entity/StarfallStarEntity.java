package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Estrela da Chuva de Estrelas: cai do alto em diagonal até a marca no chão (Telegraph só visual, sem dono), com
 * rastro de luz. No impacto: dano em área, empurrão, Exposto e uma pequena cratera.
 */
public class StarfallStarEntity extends FdSpellEntity {
    private static final EntityDataAccessor<Vector3f> FROM = SynchedEntityData.defineId(StarfallStarEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> TO = SynchedEntityData.defineId(StarfallStarEntity.class, EntityDataSerializers.VECTOR3);

    private float damage = 5F;
    private float crater = 1.3F;
    private float area = 2.6F;

    public StarfallStarEntity(EntityType<? extends StarfallStarEntity> type, Level level) {
        super(type, level);
    }

    public static void drop(SpellContext ctx, Vec3 impact, int fall, SpellStats stats) {
        ServerLevel sl = ctx.level();
        double a = sl.random.nextDouble() * Math.PI * 2;
        Vec3 from = impact.add(Math.cos(a) * 9, 26 + sl.random.nextDouble() * 6, Math.sin(a) * 9);
        StarfallStarEntity s = new StarfallStarEntity(FdRegistries.STAR.get(), sl);
        s.setPos(from.x, from.y, from.z);
        s.setup(ctx, fall + 1, stats);
        s.damage = 5F * stats.power();
        s.area = stats.radius(2.6F);
        s.crater = 1.2F + 0.5F * Math.max(0, stats.amplify);
        s.entityData.set(FROM, from.toVector3f());
        s.entityData.set(TO, impact.toVector3f());
        sl.addFreshEntity(s);
        Telegraph.show(sl, Telegraph.Shape.circle(impact, s.area).warmup(fall).color(FdFx.ASTRAL).noRunes());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(FROM, new Vector3f());
        b.define(TO, new Vector3f());
    }

    public Vec3 from() { return new Vec3(entityData.get(FROM)); }

    public Vec3 to() { return new Vec3(entityData.get(TO)); }

    /** Posição no tempo t (acelera ao cair). */
    public Vec3 at(float t) {
        float k = Mth.clamp(t / Math.max(1, maxLife() - 1), 0F, 1F);
        return from().lerp(to(), k * k * 0.6 + k * 0.4);
    }

    @Override
    public void tick() {
        setPos(at(tickCount));
        super.tick();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {}

    @Override
    protected void serverTick(ServerLevel sl) {}

    @Override
    protected void onExpire(ServerLevel sl) {
        Vec3 c = to();
        for (LivingEntity le : foes(c.add(0, 0.8, 0), area)) {
            hit(le, damage, null);
            Statuses.apply(le, Statuses.Kind.EXPOSED, 1, 80, owner(), 1F);
            Vec3 out = le.position().subtract(c).multiply(1, 0, 1);
            out = out.lengthSqr() < 1.0E-3 ? new Vec3(0, 0, 0) : out.normalize().scale(0.6);
            if (!FdUtil.isBoss(le)) FdUtil.setMotion(le, le.getDeltaMovement().add(out.x, 0.45, out.z));
        }
        Destruction.crater(sl, c, crater, owner());
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(Vfx.endRod(), c.x, c.y + 0.5, c.z, 30, 0.3, 0.3, 0.3, 0.35);
        sl.sendParticles(Vfx.firework(), c.x, c.y + 0.5, c.z, 20, 0.2, 0.2, 0.2, 0.25);
        FdFx.shockwave(sl, c.add(0, 0.2, 0), FdFx.fade(FdFx.STAR_WHITE, FdFx.ASTRAL, 1.8F), 24, 0.45);
        FdFx.sound(sl, c, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.8F, 0.7F);
        FdFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_BREAK, 1.2F, 1.5F);
        BossKit.shake(sl, c, 16, 0.6F, 8);
    }

    @Override
    protected void clientTick() {
        Vec3 p = position();
        level().addParticle(Vfx.endRod(), p.x, p.y, p.z, 0, 0.02, 0);
        level().addParticle(Vfx.firework(), p.x + (random.nextDouble() - 0.5) * 0.4, p.y, p.z + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
    }
}
