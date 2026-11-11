package com.renanpontes.isekaimahou.feature.fusions_c.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
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
 * Bola de Raio: um orbe de plasma que flutua devagar (puxado de leve para o inimigo mais próximo) e, a cada
 * {@link #ZAP_EVERY} ticks, dispara raios em até 3 inimigos próximos (os arcos são desenhados no cliente até o alvo).
 * Ao tocar um bloco ou acabar o tempo, estoura num clarão elétrico. Molhados levam ELETROCUTAR (reação do núcleo).
 */
public class BallLightningEntity extends FcSpellEntity {
    public static final int ZAP_EVERY = 12;
    private static final int MAX_PER_OWNER = 3;
    private static final EntityDataAccessor<Integer> ZAP_A = SynchedEntityData.defineId(BallLightningEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ZAP_B = SynchedEntityData.defineId(BallLightningEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ZAP_C = SynchedEntityData.defineId(BallLightningEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ZAP_AGE = SynchedEntityData.defineId(BallLightningEntity.class, EntityDataSerializers.INT);

    private Vec3 drift = Vec3.ZERO;
    private float zapRange = 7F;
    private float zapDamage = 4.5F;
    private int targets = 3;

    public BallLightningEntity(EntityType<? extends BallLightningEntity> type, Level level) {
        super(type, level);
    }

    public static void spawn(SpellContext ctx, SpellStats stats, Vec3 at, Vec3 dir) {
        ServerLevel sl = ctx.level();
        UUID owner = ctx.casterId();
        if (owner != null) {
            List<BallLightningEntity> mine = new ArrayList<>(sl.getEntities(FcRegistries.BALL_LIGHTNING.get(),
                    b -> b.isAlive() && owner.equals(b.ownerId())));
            while (mine.size() >= MAX_PER_OWNER) mine.remove(0).end();
        }
        BallLightningEntity b = new BallLightningEntity(FcRegistries.BALL_LIGHTNING.get(), sl);
        b.setPos(at.x, at.y, at.z);
        b.setup(ctx, stats, Element.LIGHTNING, stats.duration(160), 0.7F + 0.12F * Math.max(0, stats.amplify));
        b.drift = dir.normalize().scale(0.11 * stats.speedMult());
        b.zapRange = stats.radius(7F);
        b.targets = 3 + Math.max(0, stats.split);
        sl.addFreshEntity(b);
        FcFx.sound(sl, at, SoundEvents.BEACON_ACTIVATE, 1F, 1.8F);
        FcFx.sound(sl, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.6F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ZAP_A, -1);
        builder.define(ZAP_B, -1);
        builder.define(ZAP_C, -1);
        builder.define(ZAP_AGE, -100);
    }

    /** Ids das entidades atingidas pelo último disparo (-1 = nenhuma). */
    public int[] zapTargets() {
        return new int[] {entityData.get(ZAP_A), entityData.get(ZAP_B), entityData.get(ZAP_C)};
    }

    public int zapAge() { return entityData.get(ZAP_AGE); }

    @Override
    protected void serverTick(ServerLevel sl) {
        // Puxado devagar para o inimigo mais próximo (sem perder o rumo)
        List<LivingEntity> near = enemiesIn(position(), zapRange + 3);
        if (!near.isEmpty()) {
            LivingEntity t = near.get(0);
            for (LivingEntity le : near) if (le.distanceToSqr(this) < t.distanceToSqr(this)) t = le;
            Vec3 to = FcUtil.center(t).subtract(position());
            if (to.lengthSqr() > 4) drift = drift.add(to.normalize().scale(0.012));
        }
        double speed = drift.length();
        if (speed > 0.16) drift = drift.scale(0.16 / speed);
        Vec3 next = position().add(drift);
        BlockPos bp = BlockPos.containing(next);
        if (!sl.getBlockState(bp).getCollisionShape(sl, bp).isEmpty()) {
            end();
            return;
        }
        setPos(next.x, next.y, next.z);
        setDeltaMovement(drift);

        if (age() > 4 && age() % ZAP_EVERY == 0) zap(sl, near);
        if (age() % 16 == 0) FcFx.sound(sl, position(), SoundEvents.BEE_LOOP_AGGRESSIVE, 0.4F, 2F);
    }

    private void zap(ServerLevel sl, List<LivingEntity> candidates) {
        List<LivingEntity> list = new ArrayList<>(candidates);
        list.removeIf(le -> le.distanceToSqr(this) > zapRange * zapRange);
        list.sort((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this)));
        int[] ids = {-1, -1, -1};
        int n = Math.min(targets, list.size());
        for (int i = 0; i < n; i++) {
            LivingEntity le = list.get(i);
            // O dano de Raio já dá Eletrizado +1 (núcleo)
            hit(le, zapDamage);
            if (i < 3) ids[i] = le.getId();
            Vec3 c = FcUtil.center(le);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.4);
        }
        if (n == 0) return;
        entityData.set(ZAP_A, ids[0]);
        entityData.set(ZAP_B, ids[1]);
        entityData.set(ZAP_C, ids[2]);
        entityData.set(ZAP_AGE, age());
        FcFx.sound(sl, position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9F, 1.4F);
        FcFx.sound(sl, position(), SoundEvents.TRIDENT_THUNDER.value(), 0.25F, 2F);
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        Vec3 c = position();
        for (LivingEntity le : enemiesIn(c, 3.2)) {
            hit(le, 8F);
            Vec3 out = le.position().subtract(c);
            FcUtil.knock(le, out.lengthSqr() < 1.0E-3 ? new Vec3(0, 0.4, 0) : out.normalize().scale(0.6).add(0, 0.25, 0));
        }
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 50, 0.3, 0.3, 0.3, 0.9);
        FcFx.sphere(sl, c, FcFx.dust(FcFx.VOLT, 1.4F), 2.4, 40, 0);
        FcFx.sound(sl, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1.6F);
        FcFx.sound(sl, c, SoundEvents.FIREWORK_ROCKET_BLAST, 1.2F, 0.8F);
    }

    @Override
    protected void clientTick() {
        if (random.nextInt(2) == 0) {
            double a = random.nextDouble() * Math.PI * 2, b = random.nextDouble() * Math.PI;
            double r = scale() * 0.9;
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + Math.sin(b) * Math.cos(a) * r, getY() + Math.cos(b) * r,
                    getZ() + Math.sin(b) * Math.sin(a) * r, 0, 0, 0);
        }
    }
}
