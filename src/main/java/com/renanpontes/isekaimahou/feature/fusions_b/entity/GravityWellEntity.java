package com.renanpontes.isekaimahou.feature.fusions_b.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
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
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ponto de Atração: um pequeno buraco negro que puxa inimigos, itens e orbes de XP. Quem chega ao núcleo
 * leva dano a cada meio segundo; no fim ele implode com um último golpe. Chefes são puxados bem menos.
 */
public class GravityWellEntity extends FbSpellEntity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(GravityWellEntity.class, EntityDataSerializers.FLOAT);
    private static final int MAX_PER_OWNER = 2;

    private float damage = 2F;
    private float pull = 0.12F;

    public GravityWellEntity(EntityType<? extends GravityWellEntity> type, Level level) {
        super(type, level);
    }

    public static void open(SpellContext ctx, Vec3 at, float radius, int ticks, float damage, float pullStrength, SpellStats stats) {
        ServerLevel sl = ctx.level();
        UUID owner = ctx.casterId();
        if (owner != null) {
            List<GravityWellEntity> mine = new ArrayList<>(sl.getEntities(FbRegistries.GRAVITY_WELL.get(),
                    w -> w.isAlive() && owner.equals(w.ownerId())));
            while (mine.size() >= MAX_PER_OWNER) {
                GravityWellEntity oldest = mine.get(0);
                for (GravityWellEntity w : mine) if (w.life < oldest.life) oldest = w;
                oldest.end();
                mine.remove(oldest);
            }
        }
        GravityWellEntity w = new GravityWellEntity(FbRegistries.GRAVITY_WELL.get(), sl);
        w.setPos(at.x, at.y, at.z);
        w.setup(ctx, ticks);
        w.setHitStats(stats);
        w.damage = damage;
        w.pull = pullStrength;
        w.entityData.set(RADIUS, radius);
        sl.addFreshEntity(w);
        FbFx.implode(sl, at, ParticleTypes.REVERSE_PORTAL, radius * 0.6, 32, 0.3);
        FbFx.sound(sl, at, SoundEvents.PORTAL_TRIGGER, 0.6F, 1.8F);
        FbFx.sound(sl, at, SoundEvents.WARDEN_SONIC_CHARGE, 1.0F, 0.6F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RADIUS, 6F);
    }

    public float radius() { return entityData.get(RADIUS); }

    private Vec3 core() { return position().add(0, 0.5, 0); }

    @Override
    protected void serverTick(ServerLevel sl) {
        Vec3 c = core();
        float r = radius();
        AABB box = new AABB(c, c).inflate(r);
        for (Entity e : sl.getEntities((Entity) null, box, e -> e.isAlive() && e.distanceToSqr(c) <= r * r)) {
            boolean pullable = e instanceof ItemEntity || e instanceof ExperienceOrb
                    || (e instanceof LivingEntity le && !ally(le) && !FbUtil.untouchable(le));
            if (!pullable) continue;
            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double d = to.length();
            if (d < 0.05) continue;
            double strength = pull * (0.35 + (1.0 - d / r));
            if (FbUtil.isBoss(e)) strength *= 0.25;
            Vec3 add = to.scale(strength / d);
            Vec3 v = e.getDeltaMovement().scale(d < 1.5 ? 0.6 : 0.92).add(add);
            if (e instanceof ServerPlayer) FbUtil.setMotion(e, v);
            else {
                e.setDeltaMovement(v);
                e.hasImpulse = true;
            }
            if (e instanceof LivingEntity le) {
                le.resetFallDistance();
                if (d < 1.6 && tickCount % 10 == 0) {
                    hit(le, damage);
                    sl.sendParticles(ParticleTypes.CRIT, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
                }
            }
        }
        if (tickCount % 20 == 0) FbFx.sound(sl, c, SoundEvents.BEACON_AMBIENT, 0.8F, 0.5F);
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        Vec3 c = core();
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 60, 0.1, 0.1, 0.1, 0.6);
        FbFx.shockwave(sl, c, FbFx.dust(FbFx.GRAVITY, 1.6F), 32, 0.5);
        FbFx.burst(sl, c, Element.GRAVITY, 20, 0.4);
        FbFx.sound(sl, c, SoundEvents.ENDER_EYE_DEATH, 1.4F, 0.5F);
        FbFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.5F);
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(2.8),
                le -> le.isAlive() && !ally(le) && le.distanceToSqr(c) < 2.8 * 2.8)) {
            hit(le, damage * 2F);
            Vec3 out = le.position().subtract(c);
            out = out.lengthSqr() < 1.0E-3 ? new Vec3(0, 1, 0) : out.normalize();
            FbUtil.setMotion(le, new Vec3(out.x * 0.5, 0.3, out.z * 0.5));
        }
    }

    @Override
    protected void clientTick() {
        // Espiral de partículas sendo sugada para o núcleo
        float r = radius();
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = r * (0.5 + random.nextDouble() * 0.5);
            double y = (random.nextDouble() - 0.5) * 1.5;
            double x = Math.cos(a) * d, z = Math.sin(a) * d;
            // Velocidade tangencial + radial para dentro: as partículas giram enquanto caem no buraco
            double tx = -Math.sin(a), tz = Math.cos(a);
            level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + x, getY() + 0.5 + y, getZ() + z,
                    -x * 0.08 + tx * 0.12, -y * 0.08, -z * 0.08 + tz * 0.12);
        }
        if (random.nextInt(2) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level().addParticle(FbFx.dust(FbFx.GRAVITY, 1.0F), getX() + Math.cos(a) * 1.3, getY() + 0.5, getZ() + Math.sin(a) * 1.3, 0, 0, 0);
        }
    }
}
