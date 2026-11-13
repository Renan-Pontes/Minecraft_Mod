package com.renanpontes.isekaimahou.feature.end_library.entity;

import com.renanpontes.isekaimahou.feature.end_library.ElRegistries;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Arma disparada pelo Portão do Tesouro: voa reto (sem gravidade), atravessa o ar por até 3 s e crava no primeiro
 * inimigo. Não fere o dono, outros jogadores nem as invocações dele. O item mostrado (espada, machado, tridente...)
 * é sorteado no disparo e desenhado girado na direção do voo.
 */
public class TreasureBlade extends ThrowableItemProjectile {
    private static final int LIFETIME = 60;
    private float damage = 6F;

    public TreasureBlade(EntityType<? extends TreasureBlade> type, Level level) {
        super(type, level);
    }

    public static TreasureBlade create(Level level, LivingEntity owner, Vec3 from, Vec3 velocity, float damage, Item shown) {
        TreasureBlade b = new TreasureBlade(ElRegistries.TREASURE_BLADE.get(), level);
        b.setOwner(owner);
        b.setPos(from);
        b.setDeltaMovement(velocity);
        b.setItem(shown.getDefaultInstance());
        b.damage = damage;
        double h = velocity.horizontalDistance();
        b.setYRot((float) (Math.atan2(velocity.x, velocity.z) * (180.0 / Math.PI)));
        b.setXRot((float) (Math.atan2(velocity.y, h) * (180.0 / Math.PI)));
        b.yRotO = b.getYRot();
        b.xRotO = b.getXRot();
        return b;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.IRON_SWORD;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > LIFETIME) {
            discard();
        } else if (level().isClientSide && tickCount % 2 == 0) {
            level().addParticle(ParticleTypes.WAX_OFF, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) return false;
        Entity owner = getOwner();
        if (target == owner || target instanceof Player) return false;
        return !(owner != null && target instanceof OwnableEntity own && owner.getUUID().equals(own.getOwnerUUID()));
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        Entity owner = getOwner();
        Entity target = hit.getEntity();
        if (target.hurt(damageSources().thrown(this, owner), damage) && target instanceof LivingEntity le) {
            Vec3 push = getDeltaMovement().normalize().scale(0.35);
            le.push(push.x, 0.08, push.z);
        }
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 8, 0.2, 0.2, 0.2, 0.2);
        }
        playSound(SoundEvents.TRIDENT_HIT, 0.8F, 1.3F);
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.WAX_OFF, getX(), getY(), getZ(), 10, 0.2, 0.2, 0.2, 0.3);
        }
        playSound(SoundEvents.TRIDENT_HIT_GROUND, 0.7F, 1.2F);
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("BladeDamage", damage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("BladeDamage");
    }
}
