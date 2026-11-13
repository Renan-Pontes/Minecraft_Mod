package com.renanpontes.isekaimahou.feature.fire_water.entity;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.fire_water.FwRegistries;
import com.renanpontes.isekaimahou.feature.fire_water.FwUtil;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Jato d'água da Ondina: voa reto, causa dano de água, encharca e apaga o fogo do alvo. */
public class UndineWaterJet extends ThrowableProjectile {
    private float damage = 3.0F;

    public UndineWaterJet(EntityType<? extends UndineWaterJet> type, Level level) {
        super(type, level);
    }

    public UndineWaterJet(Level level, LivingEntity shooter, float damage) {
        super(FwRegistries.WATER_JET.get(), shooter, level);
        this.damage = damage;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected double getDefaultGravity() { return 0.0; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Vec3 v = getDeltaMovement();
            for (int i = 0; i < 4; i++) {
                double f = i / 4.0;
                double x = getX() - v.x * f, y = getY() + 0.15 - v.y * f, z = getZ() - v.z * f;
                level().addParticle(FwUtil.dust(i % 2 == 0 ? 0x3FA9FF : 0xA8E4FF, 1.2F), x, y, z, 0, 0, 0);
            }
            if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.SPLASH, getX(), getY() + 0.15, getZ(), 0, 0, 0);
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.FALLING_WATER, getX(), getY(), getZ(), 0, 0, 0);
            return;
        }
        if (tickCount > 40 || isInWater() && tickCount > 4) discard();
    }

    /** Dono "de verdade" do dano: o dono da Ondina (para XP e abates). */
    private Entity creditOwner() {
        Entity o = getOwner();
        if (o instanceof FwSummon s && s.getOwner() != null) return s.getOwner();
        return o;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) return false;
        Entity o = getOwner();
        return !(o instanceof FwSummon s && (s.isAlliedTo(target) || target == s));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel sl)) return;
        Entity target = result.getEntity();
        if (target instanceof LivingEntity le) {
            float mult = ElementReactions.multiplier(le.getType(), Element.WATER);
            Entity credit = creditOwner();
            if (mult > 0) {
                ElementReactions.mark(le, Element.WATER, credit != null ? credit.getUUID() : null, sl.getGameTime());
                le.invulnerableTime = 0;
                le.hurt(FwUtil.summonSpellSource(this, credit), damage * mult);
            }
            le.clearFire();
            le.removeEffect(FwRegistries.LINGERING_EMBER);
            le.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 80, 0), credit);
        }
        splash(sl, result.getLocation());
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel sl) {
            splash(sl, result.getLocation());
            discard();
        }
    }

    private void splash(ServerLevel sl, Vec3 at) {
        sl.sendParticles(Vfx.splash(), at.x, at.y, at.z, 18, 0.25, 0.25, 0.25, 0.15);
        sl.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.02);
        FwUtil.sound(sl, at, SoundEvents.GENERIC_SPLASH, 0.5F, 1.4F);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("JetDamage", damage);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.contains("JetDamage") ? tag.getFloat("JetDamage") : 3.0F;
    }
}
