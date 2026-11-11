package com.renanpontes.isekaimahou.feature.bosses_f.entity;

import com.renanpontes.isekaimahou.feature.bosses_f.BfRegistry;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bola de veneno cuspida por uma cabeça da Hidra, em arco até a marca no chão. Ao cair: dano em área (o mesmo raio
 * da marca) e uma poça venenosa que fica alguns segundos. Não quebra blocos.
 */
public class HydraVenomGlob extends ThrowableItemProjectile {
    public static final double GRAVITY = 0.05;
    public static final float RADIUS = 2.8F;
    public static final int POOL_TICKS = 100;

    private float damage = 8F;

    public HydraVenomGlob(EntityType<? extends HydraVenomGlob> type, Level level) {
        super(type, level);
    }

    /** Lança em arco balístico; devolve os ticks de voo previstos (aquecimento da marca). */
    public static int throwAt(SwampHydra owner, Vec3 from, Vec3 target, float damage) {
        HydraVenomGlob g = new HydraVenomGlob(BfRegistry.VENOM_GLOB.get(), owner.level());
        g.setOwner(owner);
        g.setPos(from.x, from.y, from.z);
        g.damage = damage;
        Vec3 d = target.subtract(from);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        double t = Mth.clamp(horiz / 0.9, 14, 36);
        double drag = 1.0 + 0.006 * t;
        g.setDeltaMovement(d.x / t * drag, (d.y + 0.5 * GRAVITY * t * t) / t, d.z / t * drag);
        owner.level().addFreshEntity(g);
        return (int) Math.ceil(t);
    }

    @Override
    protected Item getDefaultItem() {
        return BfRegistry.VENOM_GLOB_ITEM.get();
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            level().addParticle(BfFx.dust(0x8BE04A, 1.2F), getX(), getY() + 0.1, getZ(), 0, 0, 0);
            if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 0, 0, 0);
            return;
        }
        if (tickCount > 120) discard();
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        Entity owner = getOwner();
        if (owner instanceof OverworldBoss boss && e instanceof LivingEntity le && !boss.isFoe(le)) return false;
        return super.canHitEntity(e) && !(e instanceof BossPart);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
        Vec3 at = result instanceof EntityHitResult ehr ? ehr.getEntity().position() : result.getLocation();
        splash(sl, at);
        discard();
    }

    private void splash(ServerLevel sl, Vec3 p) {
        sl.sendParticles(BfFx.dust(0x8BE04A, 2.0F), p.x, p.y + 0.3, p.z, 40, RADIUS * 0.4, 0.2, RADIUS * 0.4, 0);
        sl.sendParticles(ParticleTypes.ITEM_SLIME, p.x, p.y + 0.3, p.z, 30, RADIUS * 0.4, 0.3, RADIUS * 0.4, 0.15);
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.6F, 0.5F);
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.BREWING_STAND_BREW, SoundSource.HOSTILE, 1.0F, 0.6F);
        Entity owner = getOwner();
        OverworldBoss boss = owner instanceof OverworldBoss b ? b : null;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(RADIUS + 1.5))) {
            if (boss != null && !boss.isFoe(e)) continue;
            double dx = e.getX() - p.x, dz = e.getZ() - p.z;
            if (dx * dx + dz * dz > (RADIUS + e.getBbWidth() * 0.5) * (RADIUS + e.getBbWidth() * 0.5) || Math.abs(e.getY() - p.y) > 3.0) continue;
            e.hurt(damageSources().mobProjectile(this, boss), damage);
            e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), boss);
        }
        AreaEffectCloud cloud = new AreaEffectCloud(sl, p.x, p.y, p.z);
        if (boss != null) cloud.setOwner(boss);
        cloud.setRadius(RADIUS);
        cloud.setRadiusOnUse(0F);
        cloud.setRadiusPerTick(0F);
        cloud.setWaitTime(0);
        cloud.setDuration(POOL_TICKS);
        cloud.setParticle(BfFx.dust(0x6FB83A, 1.0F));
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
        sl.addFreshEntity(cloud);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
