package com.renanpontes.isekaimahou.feature.hueco_mundo.entity;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Projétil das criaturas e chefes do módulo. Tipos: Bala (rajada branca rápida dos Vazios Alados), Cero (esfera
 * vermelha dos Vazios/Menos), Cero Verde (Arrancar de elite), Faísca de Fada (rosa, ofusca e prende) e Areia
 * (Imperatriz: cega). Some depois de 4 s ou ao bater.
 */
public class HmBolt extends Projectile {
    public static final int BALA = 0, CERO = 1, CERO_GREEN = 2, SPARKLE = 3, SAND = 4;
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(HmBolt.class, EntityDataSerializers.INT);
    private float damage = 4F;
    private int life;

    public HmBolt(EntityType<? extends HmBolt> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    /** Dispara de {@code from} na direção do alvo. */
    public static HmBolt shoot(LivingEntity owner, Vec3 from, Vec3 to, int kind, float damage, float speed) {
        HmBolt b = new HmBolt(com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries.HM_BOLT.get(), owner.level());
        b.setOwner(owner);
        b.entityData.set(DATA_KIND, kind);
        b.damage = damage;
        b.setPos(from.x, from.y, from.z);
        Vec3 d = to.subtract(from);
        b.shoot(d.x, d.y, d.z, speed, kind == BALA ? 2.0F : 1.0F);
        owner.level().addFreshEntity(b);
        return b;
    }

    public int kind() {
        return entityData.get(DATA_KIND);
    }

    public int color() {
        return switch (kind()) {
            case CERO -> HmFx.CERO_RED;
            case CERO_GREEN -> HmFx.CERO_GREEN;
            case SPARKLE -> HmFx.FEY_PINK;
            case SAND -> 0xE8D7A8;
            default -> 0xF4F2FF;
        };
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_KIND, BALA);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && ++life > 80) {
            discard();
            return;
        }
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) hitTargetOrDeflectSelf(hit);
        if (isRemoved()) return;
        Vec3 v = getDeltaMovement();
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        ProjectileUtil.rotateTowardsMovement(this, 0.5F);
        if (level().isClientSide && tickCount % 2 == 0) {
            level().addParticle(HmFx.dust(color(), kind() == BALA ? 0.8F : 1.4F), getX(), getY() + 0.25, getZ(), 0, 0, 0);
            if (kind() == SPARKLE) level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.25, getZ(), 0, 0, 0);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !HmAllies.isAlly(getOwner(), target) && !(target instanceof HmBolt);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity target = result.getEntity();
        Entity owner = getOwner();
        DamageSource src = damageSources().mobProjectile(this, owner instanceof LivingEntity le ? le : null);
        if (target.hurt(src, damage) && target instanceof LivingEntity living) {
            switch (kind()) {
                case CERO, CERO_GREEN -> living.igniteForSeconds(2F);
                case SPARKLE -> {
                    living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0), this);
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
                }
                case SAND -> living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), this);
                default -> living.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 60, 0), this);
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel sl) {
            Vec3 p = result.getLocation();
            sl.sendParticles(HmFx.dust(color(), 1.6F), p.x, p.y, p.z, 14, 0.3, 0.3, 0.3, 0.0);
            if (kind() == CERO || kind() == CERO_GREEN) sl.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            sl.playSound(null, p.x, p.y, p.z, kind() == SPARKLE ? SoundEvents.AMETHYST_BLOCK_BREAK : SoundEvents.FIRECHARGE_USE,
                    SoundSource.HOSTILE, 0.6F, 1.2F + random.nextFloat() * 0.3F);
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", kind());
        tag.putFloat("Damage", damage);
        tag.putInt("Life", life);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_KIND, Mth.clamp(tag.getInt("Kind"), 0, 4));
        damage = tag.getFloat("Damage");
        life = tag.getInt("Life");
    }
}
