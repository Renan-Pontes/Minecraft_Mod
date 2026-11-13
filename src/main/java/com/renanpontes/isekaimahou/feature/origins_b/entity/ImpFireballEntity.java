package com.renanpontes.isekaimahou.feature.origins_b.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.origins_b.ObFx;
import com.renanpontes.isekaimahou.feature.origins_b.ObRegistries;
import com.renanpontes.isekaimahou.feature.origins_b.ObUtil;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bola de fogo infernal (Diabrete e Demônio Maior). Não põe fogo em blocos; queima e fere só inimigos do dono
 * de quem atirou. A versão "grande" (do Demônio) explode em área pequena.
 */
public class ImpFireballEntity extends Fireball {
    private static final EntityDataAccessor<Boolean> DATA_BIG = SynchedEntityData.defineId(ImpFireballEntity.class, EntityDataSerializers.BOOLEAN);

    private float damage = 4.0F;
    private int age;

    public ImpFireballEntity(EntityType<? extends ImpFireballEntity> type, Level level) {
        super(type, level);
    }

    public ImpFireballEntity(Level level, LivingEntity shooter, Vec3 dir, float damage, boolean big) {
        super(ObRegistries.IMP_FIREBALL.get(), shooter, dir, level);
        this.damage = damage;
        // Começa já com velocidade (o padrão nasce quase parado e acelera); a grande é um pouco mais lenta
        this.accelerationPower = big ? 0.08 : 0.1;
        setDeltaMovement(dir.normalize().scale(big ? 0.7 : 0.9));
        entityData.set(DATA_BIG, big);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BIG, false);
    }

    public boolean isBig() {
        return entityData.get(DATA_BIG);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && ++age > 100) {
            discard();
            return;
        }
        if (level().isClientSide) {
            int n = isBig() ? 3 : 1;
            for (int i = 0; i < n; i++) {
                level().addParticle(Vfx.flame(), getX() + (random.nextDouble() - 0.5) * 0.2, getY() + 0.15,
                        getZ() + (random.nextDouble() - 0.5) * 0.2, 0, 0.01, 0);
            }
        }
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return Vfx.smoke();
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    /** Quem realmente atacou: o dono da invocação que atirou (ou ela mesma, se não tiver dono). */
    private LivingEntity credited() {
        Entity shooter = getOwner();
        if (shooter instanceof ObSummonEntity s && s.getOwner() != null) return s.getOwner();
        return shooter instanceof LivingEntity le ? le : null;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) return false;
        Entity shooter = getOwner();
        if (shooter instanceof ObSummonEntity s) return !s.isFriend(target) && target != s;
        return true;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level() instanceof ServerLevel sl && result.getEntity() instanceof LivingEntity le) {
            if (ObUtil.spellHurt(sl, le, damage, this, credited(), Element.FIRE)) {
                le.igniteForSeconds(isBig() ? 4.0F : 3.0F);
                // A rajada do Demônio puxa a atenção para ele (os diabretes, frágeis, deixam a vítima no dono)
                if (isBig() && le.isAlive() && getOwner() instanceof SummonedDemonEntity demon) le.setLastHurtByMob(demon);
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel sl) {
            Vec3 p = result.getLocation();
            if (isBig()) {
                // Estouro: fere inimigos em volta (sem quebrar blocos)
                Entity shooter = getOwner();
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.5),
                        e -> e.isAlive() && e != shooter && !(shooter instanceof ObSummonEntity s && s.isFriend(e))
                                && (!(result instanceof EntityHitResult ehr) || ehr.getEntity() != e))) {
                    if (ObUtil.spellHurt(sl, e, damage * 0.5F, this, credited(), Element.FIRE)) e.igniteForSeconds(2.0F);
                }
                sl.sendParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                ObFx.hellfireBurst(sl, p.subtract(0, 0.4, 0), 1.0);
                sl.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 0.7F, 1.4F);
            } else {
                sl.sendParticles(Vfx.flame(), p.x, p.y, p.z, 10, 0.15, 0.15, 0.15, 0.05);
                sl.sendParticles(Vfx.smoke(), p.x, p.y, p.z, 6, 0.1, 0.1, 0.1, 0.02);
                sl.playSound(null, p.x, p.y, p.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.4F, 1.6F);
            }
            discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putBoolean("Big", isBig());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        entityData.set(DATA_BIG, tag.getBoolean("Big"));
    }
}
