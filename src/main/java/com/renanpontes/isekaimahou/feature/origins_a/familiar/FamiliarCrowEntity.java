package com.renanpontes.isekaimahou.feature.origins_a.familiar;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Corvo familiar: voa em volta do dono, mergulha bicando o que o dono atacou (ou quem atacou o dono) e
 * busca itens soltos perto do dono, trazendo no bico direto para o inventário.
 */
public class FamiliarCrowEntity extends FamiliarEntity implements FlyingAnimal {
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.familiar_crow.fly");
    private static final RawAnimation HOVER = RawAnimation.begin().thenLoop("animation.familiar_crow.hover");
    private static final RawAnimation PECK = RawAnimation.begin().thenPlay("animation.familiar_crow.peck");

    private int attackCooldown;
    private int scanCooldown;
    private ItemEntity fetching;
    private Vec3 home;

    public FamiliarCrowEntity(EntityType<? extends FamiliarCrowEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setDropChance(EquipmentSlot.MAINHAND, 2.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public boolean isFlying() {
        return true;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    // =====================================================================================
    //  IA (servidor)
    // =====================================================================================
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) level();
        if (tickLife(level)) return;
        LivingEntity o = getOwner();
        keepUp(level, o, 24, 2.0);
        if (attackCooldown > 0) attackCooldown--;

        Vec3 goal;
        double maxSpeed = 0.45;
        LivingEntity target = pickTarget(o);
        if (getTarget() != target) setTarget(target);
        ItemStack carried = getMainHandItem();
        if (target != null) {
            goal = target.position().add(0, target.getBbHeight() * 0.7, 0);
            maxSpeed = 0.6;
            if (distanceToSqr(goal) < 2.0 && attackCooldown <= 0) peck(level, target);
        } else if (!carried.isEmpty()) {
            if (o == null) {
                spawnAtLevel(carried.copy());
                setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                goal = position();
            } else {
                goal = o.position().add(0, o.getBbHeight() + 0.4, 0);
                if (distanceToSqr(goal) < 3.0) deliver(level, o, carried);
            }
        } else if ((fetching = validFetch(o)) != null) {
            goal = fetching.position().add(0, 0.3, 0);
            if (distanceToSqr(goal) < 1.5) pickUp(level, fetching);
        } else if (o != null) {
            // Circula sobre o ombro do dono
            double a = tickCount * 0.05 + getId();
            goal = o.position().add(Math.cos(a) * 1.6, o.getBbHeight() + 0.6 + Math.sin(tickCount * 0.1) * 0.3, Math.sin(a) * 1.6);
            maxSpeed = 0.3;
        } else {
            if (home == null) home = position();
            double a = tickCount * 0.03 + getId();
            goal = home.add(Math.cos(a) * 3, 1.5 + Math.sin(tickCount * 0.07) * 0.5, Math.sin(a) * 3);
            maxSpeed = 0.2;
        }
        steer(goal, 0.08, maxSpeed);
        Vec3 look = target != null ? target.position().subtract(position()) : getDeltaMovement();
        if (look.horizontalDistanceSqr() > 1.0E-4) {
            float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90F;
            setYRot(Mth.rotLerp(0.35F, getYRot(), yaw));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
    }

    private void steer(Vec3 goal, double accel, double maxSpeed) {
        Vec3 to = goal.subtract(position());
        double d = to.length();
        if (d < 0.05) return;
        Vec3 v = getDeltaMovement().scale(0.9).add(to.scale(Math.min(accel, d * 0.05) / d));
        if (v.length() > maxSpeed) v = v.normalize().scale(maxSpeed);
        setDeltaMovement(v);
    }

    /** Alvo: o que o dono atacou ou quem atacou o dono, nos últimos 5s, a até 16 blocos do dono. */
    private LivingEntity pickTarget(LivingEntity o) {
        if (o == null) return null;
        LivingEntity cur = getTarget();
        if (valid(o, cur)) return cur;
        LivingEntity hurtBy = o.getLastHurtByMob();
        if (hurtBy != null && o.tickCount - o.getLastHurtByMobTimestamp() < 100 && valid(o, hurtBy)) return hurtBy;
        LivingEntity hit = o.getLastHurtMob();
        if (hit != null && o.tickCount - o.getLastHurtMobTimestamp() < 100 && valid(o, hit)) return hit;
        return null;
    }

    private boolean valid(LivingEntity o, LivingEntity t) {
        return t != null && t.isAlive() && t != this && !isAlliedTo(t) && t.level() == level() && t.distanceToSqr(o) < 16 * 16
                && !(t instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private void peck(ServerLevel level, LivingEntity target) {
        attackCooldown = 18;
        triggerAnim("action", "peck");
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * power;
        if (target.hurt(damageSources().mobAttack(this), dmg)) {
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.7, target.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
        }
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.PARROT_AMBIENT, SoundSource.NEUTRAL, 0.9F, 0.55F);
        setDeltaMovement(getDeltaMovement().scale(-0.6).add(0, 0.25, 0));
    }

    /** Item solto perto do dono que vale buscar (não jogado pelo próprio dono). */
    private ItemEntity validFetch(LivingEntity o) {
        if (o == null) return null;
        if (fetching != null && fetching.isAlive() && fetching.distanceToSqr(o) < 14 * 14) return fetching;
        fetching = null;
        if (--scanCooldown > 0) return null;
        scanCooldown = 20;
        ItemEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (ItemEntity it : level().getEntitiesOfClass(ItemEntity.class, o.getBoundingBox().inflate(10, 4, 10),
                it -> it.isAlive() && !it.hasPickUpDelay() && !it.getItem().isEmpty())) {
            // Não pega o que o dono jogou fora nem o que outro jogador largou (seria roubo)
            Entity thrower = it.getOwner();
            if (thrower != null && (thrower.getUUID().equals(owner) || thrower instanceof Player)) continue;
            double d = it.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = it;
            }
        }
        return best;
    }

    private void pickUp(ServerLevel level, ItemEntity it) {
        setItemSlot(EquipmentSlot.MAINHAND, it.getItem().copy());
        it.discard();
        fetching = null;
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5F, 1.4F);
    }

    private void deliver(ServerLevel level, LivingEntity o, ItemStack carried) {
        ItemStack give = carried.copy();
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        if (o instanceof Player p) {
            if (!p.getInventory().add(give) && !give.isEmpty()) p.drop(give, false);
        } else {
            spawnAtLevel(give);
        }
        level.playSound(null, o.getX(), o.getY(), o.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.6F);
        level.sendParticles(ParticleTypes.WITCH, getX(), getY() + 0.2, getZ(), 4, 0.1, 0.1, 0.1, 0.02);
    }

    private void spawnAtLevel(ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity drop = new ItemEntity(level(), getX(), getY(), getZ(), stack);
        drop.setDefaultPickUpDelay();
        level().addFreshEntity(drop);
    }

    @Override
    protected void onVanish(ServerLevel level) {
        spawnAtLevel(getMainHandItem().copy());
        setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.PARROT_AMBIENT;
    }

    @Override
    public float getVoicePitch() {
        return 0.55F + random.nextFloat() * 0.1F;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return SoundEvents.PARROT_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.PARROT_DEATH;
    }

    // =====================================================================================
    //  Animação (GeckoLib)
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state ->
                state.setAndContinue(getDeltaMovement().lengthSqr() > 0.02 ? FLY : HOVER)));
        controllers.add(new AnimationController<>(this, "action", 2, state -> PlayState.STOP).triggerableAnim("peck", PECK));
    }
}
