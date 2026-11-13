package com.renanpontes.isekaimahou.feature.legends_f.entity;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * Vassoura da Bruxa Viajante (homenagem a Elaina, de "A Jornada da Bruxa"): uma vassoura que voa para onde você olha.
 * W/S aceleram para frente e para trás, A/D deslizam para os lados, agachar desmonta (e você cai devagar). Deixa um
 * rastro de brilho e pétalas. É uma conjuração temporária: some quando o tempo acaba ou quando fica sem bruxa.
 * O movimento é calculado por quem pilota (como num cavalo), por isso responde na hora mesmo com latência.
 */
public class BroomEntity extends LivingEntity {
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(BroomEntity.class, EntityDataSerializers.INT);

    private int idle;

    public BroomEntity(EntityType<? extends BroomEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public void setup(float speed, int lifeTicks) {
        entityData.set(DATA_SPEED, Mth.clamp(speed, 0.3F, 2.2F));
        entityData.set(DATA_LIFE, Math.max(40, lifeTicks));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPEED, 0.75F);
        builder.define(DATA_LIFE, 1800);
    }

    public float maxSpeed() { return entityData.get(DATA_SPEED); }

    /** Ticks restantes (o cliente pisca a vassoura nos últimos 5 s). */
    public int lifeLeft() { return entityData.get(DATA_LIFE); }

    // =====================================================================================
    //  Pilotagem
    // =====================================================================================

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity first = getFirstPassenger();
        return first instanceof Player p ? p : null;
    }

    @Override
    protected void tickRidden(Player player, Vec3 travelVector) {
        super.tickRidden(player, travelVector);
        setRot(player.getYRot(), player.getXRot() * 0.5F);
        yRotO = yBodyRot = yHeadRot = getYRot();
    }

    @Override
    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return new Vec3(player.xxa, 0, player.zza);
    }

    @Override
    public void travel(Vec3 input) {
        if (!isControlledByLocalInstance()) {
            if (!level().isClientSide && getControllingPassenger() == null) {
                Vec3 v = getDeltaMovement().scale(0.8);
                setDeltaMovement(v);
                move(MoverType.SELF, v);
            }
            return;
        }
        Vec3 v = getDeltaMovement();
        if (getControllingPassenger() instanceof Player p) {
            float max = maxSpeed();
            Vec3 look = p.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            Vec3 side = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : new Vec3(flat.z, 0, -flat.x).normalize();
            Vec3 want = look.scale(input.z * max).add(side.scale(input.x * max * 0.6));
            if (input.z < 0) want = want.scale(0.5);
            // Sem comando: paira, balançando de leve
            if (Math.abs(input.z) < 0.01 && Math.abs(input.x) < 0.01) {
                want = new Vec3(0, Math.sin(tickCount * 0.12) * 0.02, 0);
            }
            v = v.lerp(want, 0.14);
        } else {
            v = v.scale(0.8);
        }
        setDeltaMovement(v);
        move(MoverType.SELF, v);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTrail();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        int life = entityData.get(DATA_LIFE) - 1;
        entityData.set(DATA_LIFE, life);
        List<Entity> riders = getPassengers();
        for (Entity r : riders) r.resetFallDistance();
        if (riders.isEmpty()) idle++;
        else idle = 0;
        if (life <= 0 || idle > 40) {
            poof(sl);
            return;
        }
        if (life == 100 && getControllingPassenger() instanceof Player p) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.isekai_mahou.legends_f.broom_ending")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), true);
        }
        if (tickCount % 30 == 0 && !riders.isEmpty() && getDeltaMovement().lengthSqr() > 0.1) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.15F, 1.6F);
        }
    }

    private void clientTrail() {
        Vec3 v = getDeltaMovement();
        if (v.lengthSqr() < 0.004) return;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 tail = position().add(Mth.sin(yaw) * 0.7, 0.2, -Mth.cos(yaw) * 0.7);
        level().addParticle(Vfx.endRod(), tail.x, tail.y, tail.z, -v.x * 0.1, -0.01, -v.z * 0.1);
        if (random.nextInt(2) == 0) {
            level().addParticle(Vfx.cherry(), tail.x + random.nextGaussian() * 0.15, tail.y, tail.z + random.nextGaussian() * 0.15, 0, 0, 0);
        }
        if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.WAX_OFF, tail.x, tail.y, tail.z, 0, 0, 0);
    }

    private void poof(ServerLevel sl) {
        for (Entity r : getPassengers()) {
            if (r instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, true));
        }
        ejectPassengers();
        sl.sendParticles(Vfx.cherry(), getX(), getY() + 0.3, getZ(), 20, 0.5, 0.2, 0.5, 0.02);
        sl.sendParticles(Vfx.endRod(), getX(), getY() + 0.3, getZ(), 10, 0.4, 0.2, 0.4, 0.04);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1F, 1.4F);
        discard();
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && passenger instanceof LivingEntity le) {
            le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false, true));
        }
    }

    // =====================================================================================
    //  Não é criatura de verdade
    // =====================================================================================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean canBeCollidedWith() { return false; }

    @Override
    public boolean isAffectedByPotions() { return false; }

    @Override
    public boolean canUsePortal(boolean allowPassengers) { return false; }

    @Override
    public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }

    @Override
    public boolean shouldShowName() { return false; }

    /** Sem "corações da montaria" no HUD: a vassoura não tem vida de verdade. */
    @Override
    public boolean showVehicleHealth() { return false; }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public boolean isNoGravity() { return true; }

    @Override
    public Iterable<ItemStack> getArmorSlots() { return List.of(); }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) { return ItemStack.EMPTY; }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}

    @Override
    public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }
}
