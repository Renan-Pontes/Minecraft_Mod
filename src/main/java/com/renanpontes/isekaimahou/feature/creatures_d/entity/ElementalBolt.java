package com.renanpontes.isekaimahou.feature.creatures_d.entity;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.creatures_d.CdEntities;

import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Projétil elemental das criaturas (cuspe de fogo, estilhaço de cristal, pulso do vazio...).
 * Voa reto, deixa rastro de partículas do elemento e aplica o efeito do elemento ao acertar.
 * O item mostrado (fire charge, cristal...) é escolhido por quem atira.
 */
public class ElementalBolt extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> ELEMENT = SynchedEntityData.defineId(ElementalBolt.class, EntityDataSerializers.INT);
    private float damage = 4.0F;

    public ElementalBolt(EntityType<? extends ElementalBolt> type, Level level) {
        super(type, level);
    }

    private ElementalBolt(LivingEntity owner, Level level) {
        super(CdEntities.ELEMENTAL_BOLT.get(), owner, level);
    }

    /** Atira um projétil do {@code owner} no {@code target}. */
    public static ElementalBolt shoot(LivingEntity owner, LivingEntity target, Element element, float damage, float speed,
                                      float inaccuracy, Item display) {
        ElementalBolt bolt = new ElementalBolt(owner, owner.level());
        bolt.entityData.set(ELEMENT, element.ordinal());
        bolt.damage = damage;
        bolt.setItem(new ItemStack(display));
        bolt.setPos(owner.getX(), owner.getEyeY() - 0.2, owner.getZ());
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(bolt.position());
        bolt.shoot(aim.x, aim.y, aim.z, speed, inaccuracy);
        owner.level().addFreshEntity(bolt);
        return bolt;
    }

    public Element element() {
        int i = entityData.get(ELEMENT);
        Element[] all = Element.values();
        return i >= 0 && i < all.length ? all[i] : Element.ARCANE;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ELEMENT, Element.ARCANE.ordinal());
    }

    @Override
    protected Item getDefaultItem() {
        return Items.FIRE_CHARGE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            ElementFx.clientTrail(level(), position(), getDeltaMovement(), element(), 0.8F);
        } else if (tickCount > 100) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = getOwner();
        if (owner != null && (target == owner || owner.isAlliedTo(target))) return false;
        return super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide) return;
        Entity target = result.getEntity();
        LivingEntity shooter = getOwner() instanceof LivingEntity l ? l : null;
        if (target.hurt(damageSources().mobProjectile(this, shooter), damage) && target instanceof LivingEntity living) {
            applyElement(living);
        }
    }

    /** Efeito característico de cada elemento. */
    private void applyElement(LivingEntity target) {
        switch (element()) {
            case FIRE -> target.igniteForSeconds(3.0F);
            case WATER -> {
                target.clearFire();
                target.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 100, 0), getOwner());
            }
            case EARTH -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), getOwner());
            case WIND -> {
                Vec3 push = getDeltaMovement().multiply(1, 0, 1).normalize().scale(0.9);
                target.push(push.x, 0.35, push.z);
                target.hurtMarked = true;
            }
            case LIGHT -> {
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0), getOwner());
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0), getOwner());
            }
            case SHADOW -> target.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, 60, 0), getOwner());
            default -> target.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 80, 0), getOwner());
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel sl) {
            ElementFx.burst(sl, result.getLocation(), element(), 12, 0.25);
            sl.playSound(null, getX(), getY(), getZ(), ElementFx.castSoundOf(element()), SoundSource.HOSTILE, 0.6F, 1.4F);
            if (element() == Element.EARTH) {
                sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.HOSTILE, 0.7F, 1.3F);
            }
            discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
        tag.putString("Element", element().id);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
        Element e = Element.byId(tag.getString("Element"));
        if (e != null) entityData.set(ELEMENT, e.ordinal());
    }
}
