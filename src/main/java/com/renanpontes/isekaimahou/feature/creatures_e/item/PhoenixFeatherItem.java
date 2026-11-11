package com.renanpontes.isekaimahou.feature.creatures_e.item;

import com.renanpontes.isekaimahou.feature.creatures_e.CeFx;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Pena de Fenix: ao usar, as chamas do renascimento curam, apagam o fogo e protegem contra ele. */
public class PhoenixFeatherItem extends CeMaterialItem {
    public PhoenixFeatherItem(Properties props) {
        super(props, true);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl) {
            player.clearFire();
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 2400, 0));
            CeFx.spiral(sl, player.position(), 0.8, 2.2, ParticleTypes.FLAME, 30, player.tickCount * 0.3);
            CeFx.burst(sl, player.position().add(0, 1, 0), ParticleTypes.TOTEM_OF_UNDYING, 20, 0.4, 0.2);
            sl.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, player.getSoundSource(), 0.8F, 1.6F);
            sl.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, player.getSoundSource(), 1.0F, 1.2F);
            player.getCooldowns().addCooldown(this, 600);
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
