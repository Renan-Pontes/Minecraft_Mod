package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.boss.BossKit;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Poço Gravitacional: um campo de gravidade multiplicada no chão. O terreno afunda esmagado, quem está dentro é
 * pregado ao chão (não pula, voadores despencam, projéteis inimigos caem) e a pressão fere a cada segundo — mais
 * ainda quem estava no ar.
 */
public class GravityFieldEntity extends FdSpellEntity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(GravityFieldEntity.class, EntityDataSerializers.FLOAT);
    private float damage = 2F;

    public GravityFieldEntity(EntityType<? extends GravityFieldEntity> type, Level level) {
        super(type, level);
    }

    public static void open(SpellContext ctx, Vec3 at, SpellStats stats) {
        ServerLevel sl = ctx.level();
        enforceCap(sl, FdRegistries.GRAVITY_FIELD.get(), ctx.casterId(), 2);
        Vec3 g = FdUtil.ground(sl, at, 8);
        GravityFieldEntity f = new GravityFieldEntity(FdRegistries.GRAVITY_FIELD.get(), sl);
        f.setPos(g.x, g.y, g.z);
        f.setup(ctx, stats.duration(120), stats);
        f.damage = 2.2F * stats.power();
        float r = Math.min(14F, stats.radius(6F));
        f.entityData.set(RADIUS, r);
        sl.addFreshEntity(f);
        // O chão afunda sob o peso
        Destruction.surface(sl, g, r * 0.85F, ctx.caster(), Destruction.Mode.CRUSH);
        FdFx.shockwave(sl, g.add(0, 0.2, 0), FdFx.fade(FdFx.GRAVITY, 0x120A20, 2.4F), 40, 0.5);
        FdFx.sound(sl, g, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2F, 0.4F);
        FdFx.sound(sl, g, SoundEvents.WARDEN_SONIC_CHARGE, 1.2F, 0.4F);
        BossKit.shake(sl, g, r + 8, 1.0F, 14);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(RADIUS, 6F);
    }

    public float radius() { return entityData.get(RADIUS); }

    @Override
    protected void serverTick(ServerLevel sl) {
        Vec3 c = position();
        float r = radius();
        AABB box = new AABB(c.x - r, c.y - 2, c.z - r, c.x + r, c.y + 12, c.z + r);
        boolean pulse = tickCount % 20 == 0;
        for (Entity e : sl.getEntities((Entity) null, box, e -> e.isAlive() && horiz(e, c) <= r * r)) {
            if (e instanceof Projectile p) {
                if (p.getOwner() == null || !ally(p.getOwner())) p.setDeltaMovement(p.getDeltaMovement().multiply(0.8, 1, 0.8).add(0, -0.25, 0));
                continue;
            }
            if (!(e instanceof LivingEntity le) || ally(le) || FdUtil.untouchable(le) || le.isSpectator()) continue;
            boolean boss = FdUtil.isBoss(le);
            boolean air = !le.onGround() && !le.isInWater();
            Vec3 v = le.getDeltaMovement();
            double down = boss ? -0.12 : -0.55;
            FdUtil.setMotion(le, new Vec3(v.x * 0.55, Math.min(v.y, 0) + (air ? down : -0.1), v.z * 0.55));
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, boss ? 1 : 3, false, false, true), owner());
            if (pulse) {
                hit(le, air ? damage * 2.2F : damage, null);
                sl.sendParticles(ParticleTypes.CRIT, le.getX(), le.getY() + 0.2, le.getZ(), 6, 0.3, 0.05, 0.3, 0.1);
            }
        }
        if (pulse) {
            FdFx.ring(sl, c.add(0, 0.15, 0), FdFx.dust(FdFx.GRAVITY, 1.6F), r, 40, 0);
            FdFx.sound(sl, c, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.2F, 0.5F);
        }
    }

    private static double horiz(Entity e, Vec3 c) {
        double dx = e.getX() - c.x, dz = e.getZ() - c.z;
        return dx * dx + dz * dz;
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        FdFx.shockwave(sl, position().add(0, 0.3, 0), ParticleTypes.REVERSE_PORTAL, 30, 0.4);
        FdFx.sound(sl, position(), SoundEvents.BEACON_DEACTIVATE, 1.2F, 0.5F);
    }

    @Override
    protected void clientTick() {
        float r = radius();
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2, d = Math.sqrt(random.nextDouble()) * r;
            level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + Math.cos(a) * d, getY() + 3 + random.nextDouble() * 4,
                    getZ() + Math.sin(a) * d, 0, -0.6, 0);
        }
    }
}
