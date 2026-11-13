package com.renanpontes.isekaimahou.feature.mounts.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Cervo da Aurora: cervo branco de galhada luminosa, presente do Reino a quem se torna HONRADO (reputação).
 * Não se machuca com queda (nem o cavaleiro) e é a montaria das montanhas: a habilidade "Salto Boreal"
 * dá um pulo alto e longo na direção do olhar, com queda suave.
 */
public class AuroraStag extends GroundMount {
    public static final String ID = "aurora_stag";
    /** O salto desta ativação já foi dado (lado que controla o movimento). */
    private boolean leaped;

    public AuroraStag(EntityType<? extends AuroraStag> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.31)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected boolean isHealingFood(ItemStack stack) {
        return stack.is(Items.APPLE) || stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES) || stack.is(Items.WHEAT);
    }

    @Override
    protected double wildMaxHealth() {
        return 40.0;
    }

    @Override
    protected double tamedMaxHealth() {
        return 48.0;
    }

    @Override
    public String abilityId() {
        return "stag_leap";
    }

    @Override
    public int abilityCooldownMax() {
        return 140;
    }

    @Override
    protected int abilityDuration() {
        return 50;
    }

    @Override
    protected boolean startAbility(ServerPlayer rider) {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, getSoundSource(), 1.4F, 0.8F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.GOAT_LONG_JUMP, getSoundSource(), 1.0F, 1.0F);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 12, 0.5, 0.1, 0.5, 0.03);
        }
        return true;
    }

    /** O movimento da montaria pilotada é do cliente: é ele que dá o impulso quando vê a habilidade ligar. */
    @Override
    protected void riddenControlTick(Player rider) {
        if (abilityActive() <= 0) {
            leaped = false;
            return;
        }
        Vec3 mv = getDeltaMovement();
        if (!leaped) {
            leaped = true;
            Vec3 look = Vec3.directionFromRotation(0.0F, getYRot());
            setDeltaMovement(look.x * 1.15, 0.92, look.z * 1.15);
            hasImpulse = true;
        } else if (mv.y < -0.18) {
            // Queda suave até o efeito acabar
            setDeltaMovement(mv.x, -0.18, mv.z);
        }
    }

    @Override
    protected void abilityTick(int ticksLeft) {
        if (ticksLeft % 3 == 0 && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.2, getZ(), 1, 0.3, 0.1, 0.3, 0.0);
        }
    }

    /** Nem o cervo nem quem está montado se machuca com queda. */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.GOAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GOAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GOAT_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.GOAT_STEP, 0.3F, 1.1F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.7F;
    }
}
