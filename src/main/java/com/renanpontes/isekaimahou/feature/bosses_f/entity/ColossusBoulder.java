package com.renanpontes.isekaimahou.feature.bosses_f.entity;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.bosses_f.BfRegistry;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Rocha do Colosso: arremessada em arco (cai na marca do chão) ou caindo do céu na Chuva de Pedras. No impacto: dano no
 * raio da marca, empurrão e uma cratera de estilhaços só visual
 * (v1.0: lutas de chefe não destroem o cenário).
 */
public class ColossusBoulder extends ThrowableProjectile {
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(ColossusBoulder.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_MOSSY = SynchedEntityData.defineId(ColossusBoulder.class, EntityDataSerializers.BOOLEAN);
    public static final double GRAVITY = 0.05;

    private float damage = 14F;
    private float radius = 3.2F;
    private float crater = 2.2F;
    private boolean falling;

    public ColossusBoulder(EntityType<? extends ColossusBoulder> type, Level level) {
        super(type, level);
    }

    private static ColossusBoulder create(RuinColossus owner, Vec3 from, float size, float damage, float radius, float crater) {
        ColossusBoulder b = new ColossusBoulder(BfRegistry.BOULDER.get(), owner.level());
        b.setOwner(owner);
        b.setPos(from.x, from.y, from.z);
        b.entityData.set(DATA_SIZE, size);
        b.entityData.set(DATA_MOSSY, owner.getRandom().nextBoolean());
        b.damage = damage;
        b.radius = radius;
        b.crater = crater;
        return b;
    }

    /** Arremesso em arco até {@code target}; devolve os ticks de voo (aquecimento da marca). */
    public static int throwAt(RuinColossus owner, Vec3 from, Vec3 target, float size, float damage, float radius, float crater) {
        ColossusBoulder b = create(owner, from, size, damage, radius, crater);
        Vec3 d = target.subtract(from);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        double t = Mth.clamp(horiz / 1.0, 16, 40);
        double drag = 1.0 + 0.006 * t;
        b.setDeltaMovement(d.x / t * drag, (d.y + 0.5 * GRAVITY * t * t) / t, d.z / t * drag);
        owner.level().addFreshEntity(b);
        return (int) Math.ceil(t);
    }

    /** Rocha caindo reto do céu, sem gravidade (velocidade constante: chega no tempo certo). */
    public static void dropFrom(RuinColossus owner, Vec3 top, double speed, float size, float damage, float radius, float crater) {
        ColossusBoulder b = create(owner, top, size, damage, radius, crater);
        b.falling = true;
        b.setNoGravity(true);
        b.setDeltaMovement(0, -speed, 0);
        owner.level().addFreshEntity(b);
    }

    public float size() {
        return entityData.get(DATA_SIZE);
    }

    public boolean mossy() {
        return entityData.get(DATA_MOSSY);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SIZE, 1.0F);
        builder.define(DATA_MOSSY, false);
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    @Override
    public void tick() {
        Vec3 v = getDeltaMovement();
        super.tick();
        // Queda reta: sem o arrasto do ar (a marca foi cronometrada com velocidade constante).
        if (falling && !isRemoved()) setDeltaMovement(v);
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState()),
                        getX() + (random.nextDouble() - 0.5) * size(), getY() + 0.4, getZ() + (random.nextDouble() - 0.5) * size(), 0, 0, 0);
            }
            return;
        }
        if (tickCount > 200) discard();
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
        impact(sl, result.getLocation());
        discard();
    }

    private void impact(ServerLevel sl, Vec3 p) {
        float s = size();
        BlockState rock = mossy() ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, rock), p.x, p.y + 0.4, p.z, (int) (50 * s), 0.7 * s, 0.4, 0.7 * s, 0.25);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, BfFx.ground(sl, p)), p.x, p.y + 0.2, p.z, (int) (30 * s), radius * 0.4, 0.2,
                radius * 0.4, 0.25);
        sl.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y + 0.5, p.z, 1, 0, 0, 0, 0);
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.4F * s, 0.5F);
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 1.8F, 0.5F);
        Entity owner = getOwner();
        OverworldBoss boss = owner instanceof OverworldBoss b ? b : null;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius + 1.5))) {
            if (boss != null && !boss.isFoe(e)) continue;
            double dx = e.getX() - p.x, dz = e.getZ() - p.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > radius + e.getBbWidth() * 0.5 || Math.abs(e.getY() - p.y) > 3.5) continue;
            float dmg = (float) (damage * Math.max(0.55, 1.0 - d / (radius + 2.0)));
            if (e.hurt(damageSources().mobProjectile(this, boss), dmg)) {
                Vec3 push = d < 0.1 ? new Vec3(0, 0, 0) : new Vec3(dx / d, 0, dz / d);
                e.setDeltaMovement(e.getDeltaMovement().add(push.x * 0.7 * s, 0.45 * s, push.z * 0.7 * s));
                e.hurtMarked = true;
            }
        }
        if (crater > 0) {
            BfFx.impactCrater(sl, p, crater);
            BossKit.shake(sl, p, 22, 1.0F, 12);
        } else {
            BossKit.shake(sl, p, 18, 0.8F, 10);
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
