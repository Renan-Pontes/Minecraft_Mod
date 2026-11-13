package com.renanpontes.isekaimahou.feature.mounts.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pesadelo: corcel negro de crina em chamas. Não existe solto no mundo — as Rédeas caem de CHEFES.
 * Imune a fogo e protege o cavaleiro do fogo enquanto montado. Habilidade "Galope Infernal": corrida veloz
 * que queima os inimigos por onde passa (só fere criaturas: nunca acende fogo nos blocos).
 */
public class Nightmare extends GroundMount {
    public static final String ID = "nightmare";

    public Nightmare(EntityType<? extends Nightmare> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 50.0)
                .add(Attributes.MOVEMENT_SPEED, 0.33)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected boolean isHealingFood(ItemStack stack) {
        return stack.is(Items.BLAZE_POWDER) || stack.is(Items.MAGMA_CREAM) || stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
    }

    @Override
    protected double wildMaxHealth() {
        return 50.0;
    }

    @Override
    protected double tamedMaxHealth() {
        return 60.0;
    }

    @Override
    public String abilityId() {
        return "nightmare_gallop";
    }

    @Override
    public int abilityCooldownMax() {
        return 400;
    }

    @Override
    protected int abilityDuration() {
        return 120;
    }

    @Override
    protected double speedMultiplier() {
        return abilityActive() > 0 ? 1.55 : 1.0;
    }

    @Override
    protected boolean startAbility(ServerPlayer rider) {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, getSoundSource(), 1.0F, 0.6F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.HORSE_ANGRY, getSoundSource(), 1.0F, 0.6F);
        return true;
    }

    @Override
    protected void abilityTick(int ticksLeft) {
        if (!(level() instanceof ServerLevel sl)) return;
        sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.15, getZ(), 2, 0.3, 0.05, 0.3, 0.01);
        if (ticksLeft % 10 != 0) return;
        LivingEntity rider = getControllingPassenger();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.0, 0.3, 1.0))) {
            if (MountFx.isFriendly(this, e, rider)) continue;
            var src = rider instanceof Player p ? damageSources().playerAttack(p) : damageSources().mobAttack(this);
            if (e.hurt(src, 4.0F)) e.igniteForSeconds(3.0F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // Crina em brasa (discreto: uma chama pequena de vez em quando)
            if (random.nextInt(4) == 0) {
                level().addParticle(ParticleTypes.SMALL_FLAME, getRandomX(0.4), getY() + getBbHeight() * 0.85, getRandomZ(0.4), 0, 0.02, 0);
            }
        } else if (tickCount % 40 == 0 && getControllingPassenger() instanceof Player p) {
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100, 0, true, false, false));
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HORSE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HORSE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HORSE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.HORSE_GALLOP, 0.35F, 0.8F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6F;
    }
}
