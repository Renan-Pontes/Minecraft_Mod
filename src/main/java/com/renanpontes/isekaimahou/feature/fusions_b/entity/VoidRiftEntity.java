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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rasgo do Vazio: uma fenda vertical no espaço (desenhada com o "céu do End") que fere quem está perto a cada
 * meio segundo e puxa de leve para dentro. Abre e fecha com animação; no máximo 2 por conjurador.
 */
public class VoidRiftEntity extends FbSpellEntity {
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(VoidRiftEntity.class, EntityDataSerializers.FLOAT);
    private static final int MAX_PER_OWNER = 2;
    public static final float HEIGHT = 3.0F;

    private float damage = 2F;

    public VoidRiftEntity(EntityType<? extends VoidRiftEntity> type, Level level) {
        super(type, level);
    }

    public static void open(SpellContext ctx, Vec3 at, float yaw, float radius, int ticks, float damage, SpellStats stats) {
        ServerLevel sl = ctx.level();
        UUID owner = ctx.casterId();
        if (owner != null) {
            List<VoidRiftEntity> mine = new ArrayList<>(sl.getEntities(FbRegistries.VOID_RIFT.get(),
                    r -> r.isAlive() && owner.equals(r.ownerId())));
            while (mine.size() >= MAX_PER_OWNER) {
                VoidRiftEntity oldest = mine.get(0);
                for (VoidRiftEntity r : mine) if (r.life < oldest.life) oldest = r;
                oldest.end();
                mine.remove(oldest);
            }
        }
        VoidRiftEntity r = new VoidRiftEntity(FbRegistries.VOID_RIFT.get(), sl);
        r.setPos(at.x, at.y, at.z);
        r.setYRot(yaw);
        r.setup(ctx, ticks);
        r.setHitStats(stats);
        r.damage = damage;
        r.entityData.set(RADIUS, radius);
        sl.addFreshEntity(r);
        Vec3 mid = at.add(0, HEIGHT * 0.5, 0);
        sl.sendParticles(ParticleTypes.PORTAL, mid.x, mid.y, mid.z, 50, 0.2, HEIGHT * 0.4, 0.2, 0.8);
        FbFx.sound(sl, mid, SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.4F);
        FbFx.sound(sl, mid, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.0F, 0.6F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RADIUS, 2.5F);
    }

    public float radius() { return entityData.get(RADIUS); }

    @Override
    protected void serverTick(ServerLevel sl) {
        if (maxLife() - life < 8) return; // ainda abrindo
        Vec3 mid = position().add(0, HEIGHT * 0.5, 0);
        float r = radius();
        boolean pulse = tickCount % 10 == 0;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(mid, mid).inflate(r, HEIGHT * 0.5 + 0.5, r),
                e -> e.isAlive() && !ally(e) && !FbUtil.untouchable(e))) {
            Vec3 to = new Vec3(getX() - e.getX(), 0, getZ() - e.getZ());
            double d = to.length();
            if (d > r) continue;
            if (d > 0.4 && !FbUtil.isBoss(e)) {
                Vec3 pull = to.scale(0.04 / d);
                if (e instanceof ServerPlayer) FbUtil.addMotion(e, pull);
                else e.setDeltaMovement(e.getDeltaMovement().add(pull));
            }
            if (pulse) {
                hit(e, damage);
                sl.sendParticles(ParticleTypes.REVERSE_PORTAL, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6,
                        0.2, 0.3, 0.2, 0.05);
            }
        }
        if (tickCount % 25 == 0) FbFx.sound(sl, mid, SoundEvents.PORTAL_AMBIENT, 0.5F, 1.4F);
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        Vec3 mid = position().add(0, HEIGHT * 0.5, 0);
        FbFx.burst(sl, mid, Element.VOID, 20, 0.4);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, mid.x, mid.y, mid.z, 40, 0.1, HEIGHT * 0.4, 0.1, 0.15);
        FbFx.sound(sl, mid, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 1.6F);
    }

    @Override
    protected void clientTick() {
        // Fiapos de vazio escapando da fenda
        float open = openness(0, 8);
        if (open <= 0) return;
        for (int i = 0; i < 2; i++) {
            double y = getY() + random.nextDouble() * HEIGHT;
            level().addParticle(ParticleTypes.PORTAL, getX(), y, getZ(),
                    (random.nextDouble() - 0.5) * 1.5, (random.nextDouble() - 0.5) * 0.5, (random.nextDouble() - 0.5) * 1.5);
        }
        if (random.nextInt(3) == 0) {
            level().addParticle(FbFx.dust(FbFx.VOID, 1.0F), getX() + (random.nextDouble() - 0.5) * 0.4,
                    getY() + random.nextDouble() * HEIGHT, getZ() + (random.nextDouble() - 0.5) * 0.4, 0, 0, 0);
        }
    }
}
