package com.renanpontes.isekaimahou.feature.fusions_d.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Fenda do Vazio: dois rasgos no espaço. A ENTRADA, no alvo, suga inimigos e itens; quem cai nela sai pela SAÍDA,
 * aberta bem alto no céu, cuspido com força (e cai de lá). Chefes não passam: levam dano e ficam Expostos.
 */
public class VoidTearEntity extends FdSpellEntity {
    private static final EntityDataAccessor<Boolean> EXIT = SynchedEntityData.defineId(VoidTearEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(VoidTearEntity.class, EntityDataSerializers.FLOAT);

    private VoidTearEntity partner;
    private float damage = 5F;
    private float radius = 7F;
    /** Quem acabou de passar (não é puxado de volta logo em seguida). */
    private final Map<UUID, Integer> recent = new HashMap<>();

    public VoidTearEntity(EntityType<? extends VoidTearEntity> type, Level level) {
        super(type, level);
    }

    public static void open(SpellContext ctx, Vec3 at, SpellStats stats) {
        ServerLevel sl = ctx.level();
        enforceCap(sl, FdRegistries.VOID_TEAR.get(), ctx.casterId(), 2);
        Vec3 g = FdUtil.ground(sl, at, 6).add(0, 0.2, 0);
        int life = stats.duration(90);
        VoidTearEntity in = new VoidTearEntity(FdRegistries.VOID_TEAR.get(), sl);
        in.setPos(g.x, g.y, g.z);
        in.setYRot(ctx.caster() != null ? ctx.caster().getYRot() + 90F : 0F);
        in.setup(ctx, life, stats);
        in.damage = 5F * stats.power();
        in.radius = Math.min(14F, stats.radius(7F));
        in.entityData.set(SIZE, 1.0F + 0.15F * stats.aoe);
        VoidTearEntity out = new VoidTearEntity(FdRegistries.VOID_TEAR.get(), sl);
        Vec3 sky = exitSpot(sl, g, 14 + 4 * Math.max(0, stats.amplify));
        out.setPos(sky.x, sky.y, sky.z);
        out.setYRot(sl.random.nextFloat() * 360F);
        out.setup(ctx, life, stats);
        out.entityData.set(EXIT, true);
        out.entityData.set(SIZE, 0.8F);
        in.partner = out;
        out.partner = in;
        // A saída só é "dona" de vida pelo par: some junto com a entrada
        out.ownerId = null;
        sl.addFreshEntity(in);
        sl.addFreshEntity(out);
        FdFx.sound(sl, g, SoundEvents.END_PORTAL_SPAWN, 1.0F, 1.4F);
        FdFx.sound(sl, g, SoundEvents.WARDEN_SONIC_CHARGE, 1.2F, 1.2F);
        FdFx.sound(sl, sky, SoundEvents.PORTAL_TRIGGER, 1.0F, 1.6F);
    }

    /** Ponto livre no céu acima (sobe até achar ar, sem atravessar o teto do mundo). */
    private static Vec3 exitSpot(ServerLevel sl, Vec3 g, int up) {
        double a = sl.random.nextDouble() * Math.PI * 2;
        Vec3 p = g.add(Math.cos(a) * 5, up, Math.sin(a) * 5);
        for (int i = up; i > 3; i--) {
            Vec3 q = g.add(Math.cos(a) * 5, i, Math.sin(a) * 5);
            BlockPos b = BlockPos.containing(q);
            if (sl.isLoaded(b) && sl.getBlockState(b).isAir() && sl.getBlockState(b.above()).isAir() && sl.getBlockState(b.below()).isAir()) return q;
        }
        return p.y > sl.getMaxBuildHeight() - 4 ? g.add(0, 4, 0) : p;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(EXIT, false);
        b.define(SIZE, 1F);
    }

    public boolean isExit() { return entityData.get(EXIT); }

    public float size() { return entityData.get(SIZE); }

    @Override
    protected void serverTick(ServerLevel sl) {
        if (isExit()) {
            if (partner == null || !partner.isAlive()) discard();
            return;
        }
        if (partner == null || !partner.isAlive()) {
            discard();
            return;
        }
        recent.replaceAll((k, v) -> v - 1);
        recent.values().removeIf(v -> v <= 0);
        Vec3 c = position().add(0, 1.4 * size(), 0);
        float ramp = Math.min(1F, tickCount / 12F);
        for (Entity e : sl.getEntities((Entity) null, new AABB(c, c).inflate(radius), e -> e.isAlive() && e.distanceToSqr(c) <= radius * radius)) {
            boolean living = e instanceof LivingEntity le && !ally(le) && !FdUtil.untouchable(le) && !le.isSpectator();
            if (!living && !(e instanceof ItemEntity)) continue;
            if (recent.containsKey(e.getUUID())) continue;
            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double d = to.length();
            if (d < 1.4) {
                swallow(sl, e);
                continue;
            }
            double k = ramp * (FdUtil.isBoss(e) ? 0.03 : 0.13) * (0.5 + (1 - d / radius));
            Vec3 v = e.getDeltaMovement().scale(0.88).add(to.scale(k / d));
            if (e instanceof ServerPlayer) FdUtil.setMotion(e, v);
            else {
                e.setDeltaMovement(v);
                e.hasImpulse = true;
            }
        }
        if (tickCount % 20 == 0) FdFx.sound(sl, c, SoundEvents.PORTAL_AMBIENT, 1.2F, 0.6F);
    }

    private void swallow(ServerLevel sl, Entity e) {
        recent.put(e.getUUID(), 30);
        if (e instanceof LivingEntity le && FdUtil.isBoss(le)) {
            hit(le, damage * 1.6F, null);
            Statuses.apply(le, Statuses.Kind.EXPOSED, 2, 120, owner(), 1F);
            Vec3 out = le.position().subtract(position()).multiply(1, 0, 1);
            if (out.lengthSqr() > 1.0E-3) le.setDeltaMovement(out.normalize().scale(0.8));
            return;
        }
        Vec3 from = e.position();
        Vec3 ex = partner.position();
        float yaw = partner.getYRot() * ((float) Math.PI / 180F);
        Vec3 push = new Vec3(Math.cos(yaw) * 1.1, 0.6, Math.sin(yaw) * 1.1);
        if (e instanceof ServerPlayer sp) sp.teleportTo(sl, ex.x, ex.y, ex.z, sp.getYRot(), sp.getXRot());
        else e.teleportTo(ex.x, ex.y, ex.z);
        FdUtil.setMotion(e, push);
        if (e instanceof LivingEntity le) {
            hit(le, damage, null);
            Statuses.apply(le, Statuses.Kind.EXPOSED, 1, 100, owner(), 1F);
            FdUtil.forgetTarget(le);
        }
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.6, 0.3, 0.2);
        sl.sendParticles(ParticleTypes.PORTAL, ex.x, ex.y, ex.z, 40, 0.4, 0.6, 0.4, 0.6);
        sl.sendParticles(FdFx.dust(FdFx.VOID_EDGE, 1.5F), ex.x, ex.y, ex.z, 20, 0.5, 0.5, 0.5, 0);
        FdFx.sound(sl, from, SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.6F);
        FdFx.sound(sl, ex, SoundEvents.SHULKER_SHOOT, 1.4F, 0.5F);
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        Vec3 c = position().add(0, 1.2, 0);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 40, 0.2, 0.8, 0.2, 0.4);
        FdFx.sound(sl, c, SoundEvents.ENDER_EYE_DEATH, 1.2F, 0.6F);
        if (!isExit() && partner != null && partner.isAlive()) partner.end();
    }

    @Override
    protected void clientTick() {
        double h = 1.4 * size();
        double a = random.nextDouble() * Math.PI * 2, d = isExit() ? 0.6 : 3 + random.nextDouble() * 3;
        if (isExit()) {
            level().addParticle(ParticleTypes.PORTAL, getX(), getY() + h, getZ(), Math.cos(a) * 0.4, (random.nextDouble() - 0.5) * 0.4, Math.sin(a) * 0.4);
        } else {
            level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + Math.cos(a) * d, getY() + h + (random.nextDouble() - 0.5) * 2,
                    getZ() + Math.sin(a) * d, -Math.cos(a) * 0.25, 0, -Math.sin(a) * 0.25);
        }
    }
}
