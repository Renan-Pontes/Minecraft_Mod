package com.renanpontes.isekaimahou.feature.mounts.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dragão Terrestre: o "cavalo" dos reinos — lagarto corredor de duas patas fortes, manso e resistente.
 * É a PRIMEIRA montaria do jogador: as Rédeas são vendidas na loja da Guilda; também vive solto nas savanas
 * e planícies e aceita carne crua (1 chance em 4). Habilidade "Arrancada": corrida curta bem mais rápida.
 */
public class LandDrake extends GroundMount {
    public static final String ID = "land_drake";

    public LandDrake(EntityType<? extends LandDrake> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.29)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected InteractionResult interactWild(Player player, InteractionHand hand, ItemStack stack) {
        boolean client = level().isClientSide;
        if (!stack.is(ItemTags.MEAT)) {
            // Mão vazia numa selvagem: diz o que ela quer (é assim que o jogador descobre a doma)
            if (!stack.isEmpty()) return InteractionResult.PASS;
            if (!client) player.displayClientMessage(tameHint(), true);
            return InteractionResult.sidedSuccess(client);
        }
        if (!client) {
            stack.consume(1, player);
            playSound(SoundEvents.GENERIC_EAT, 0.9F, 0.7F);
            tryTame(player, 4);
        }
        return InteractionResult.sidedSuccess(client);
    }

    @Override
    protected boolean isHealingFood(ItemStack stack) {
        return stack.is(ItemTags.MEAT);
    }

    @Override
    protected double wildMaxHealth() {
        return 30.0;
    }

    @Override
    protected double tamedMaxHealth() {
        return 40.0;
    }

    @Override
    public String abilityId() {
        return "drake_sprint";
    }

    @Override
    public int abilityCooldownMax() {
        return 300;
    }

    @Override
    protected int abilityDuration() {
        return 100;
    }

    @Override
    protected double speedMultiplier() {
        return abilityActive() > 0 ? 1.6 : 1.0;
    }

    @Override
    protected boolean startAbility(ServerPlayer rider) {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, getSoundSource(), 0.6F, 1.5F);
        return true;
    }

    @Override
    protected void abilityTick(int ticksLeft) {
        if (ticksLeft % 2 == 0 && onGround() && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.1, getZ(), 1, 0.25, 0.02, 0.25, 0.01);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.CAMEL_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CAMEL_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CAMEL_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.CAMEL_STEP, 0.4F, 1.2F);
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.35F;
    }
}
