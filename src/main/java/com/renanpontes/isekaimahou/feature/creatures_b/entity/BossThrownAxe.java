package com.renanpontes.isekaimahou.feature.creatures_b.entity;

import javax.annotation.Nullable;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Arma arremessada por um chefe (cutelo do Lorde Orc, lábris do Minotauro): voa em linha RETA (sem gravidade, igual
 * à linha telegrafada no chão), girando; acerta a primeira criatura inimiga no caminho com dano e um empurrão forte
 * (derruba quem está em pilares) ou bate num bloco e se despedaça. Não é salva no mundo (dura poucos segundos).
 */
public class BossThrownAxe extends Projectile {
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(BossThrownAxe.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(BossThrownAxe.class, EntityDataSerializers.FLOAT);
    private static final int MAX_LIFE = 60;

    private float damage = 8.0F;
    private double knock = 1.2;
    private double maxRange = 24.0;
    private Vec3 start = Vec3.ZERO;
    private int life;

    public BossThrownAxe(EntityType<? extends BossThrownAxe> type, Level level) {
        super(type, level);
    }

    /** Lança a arma de {@code from} na direção {@code dir} (normalizada). Devolve null se o tipo não estiver registrado. */
    @Nullable
    public static BossThrownAxe launch(ServerLevel level, Mob owner, Vec3 from, Vec3 dir, double speed, float damage, double knock,
                                       double range, ItemStack weapon, float scale) {
        BossThrownAxe axe = CbBossRegistry.THROWN_AXE.get().create(level);
        if (axe == null) return null;
        axe.setOwner(owner);
        axe.setPos(from.x, from.y, from.z);
        axe.start = from;
        axe.damage = damage;
        axe.knock = knock;
        axe.maxRange = range;
        axe.entityData.set(DATA_ITEM, weapon.copyWithCount(1));
        axe.entityData.set(DATA_SCALE, scale);
        axe.shoot(dir.x, dir.y, dir.z, (float) speed, 0.0F);
        level.addFreshEntity(axe);
        return axe;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
        builder.define(DATA_SCALE, 1.5F);
    }

    public ItemStack getItem() {
        return entityData.get(DATA_ITEM);
    }

    public float renderScale() {
        return entityData.get(DATA_SCALE);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                hitTargetOrDeflectSelf(hit);
                if (isRemoved()) return;
            }
            life++;
            if (life > MAX_LIFE || position().distanceToSqr(start) > maxRange * maxRange) {
                fizzle();
                return;
            }
            if (life % 5 == 1) {
                level().playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.7F, 0.5F);
            }
        } else if (tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.CRIT, getX(), getY() + 0.2, getZ(), -v.x * 0.1, 0.02, -v.z * 0.1);
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) return false;
        Entity owner = getOwner();
        if (target == owner || !(target instanceof LivingEntity)) return false;
        if (owner != null && owner.isAlliedTo(target)) return false;
        return !(target instanceof Player p) || (!p.isCreative() && !p.isSpectator());
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity e = result.getEntity();
        LivingEntity thrower = getOwner() instanceof LivingEntity le ? le : null;
        Vec3 dir = getDeltaMovement().multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.ZERO : dir.normalize();
        if (e.hurt(damageSources().mobProjectile(this, thrower), damage) && e instanceof LivingEntity le) {
            double resist = 1.0 - le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            le.push(dir.x * knock * resist, 0.4 * resist, dir.z * knock * resist);
            le.hurtMarked = true;
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), thrower);
        }
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()), getX(), getY(), getZ(), 14, 0.2, 0.2, 0.2, 0.15);
            sl.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 16, 0.3, 0.3, 0.3, 0.4);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_HIT, SoundSource.HOSTILE, 1.2F, 0.6F);
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.5F, 1.4F);
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel sl) {
            Vec3 at = result.getLocation();
            BlockState state = sl.getBlockState(result.getBlockPos());
            if (!state.isAir()) {
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 16, 0.2, 0.2, 0.2, 0.15);
            }
            sl.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.3);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 0.8F, 0.8F);
        }
        discard();
    }

    /** Chegou ao fim do alcance: cai e some numa nuvem de poeira. */
    private void fizzle() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 8, 0.2, 0.2, 0.2, 0.02);
        }
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
