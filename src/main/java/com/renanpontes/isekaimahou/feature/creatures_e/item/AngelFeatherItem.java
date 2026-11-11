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

/** Pena de Anjo: ao usar, deixa o corpo leve (Queda Lenta longa) e mais rapido por um tempo. */
public class AngelFeatherItem extends CeMaterialItem {
    public AngelFeatherItem(Properties props) {
        super(props, false);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 1800, 0));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
            CeFx.ring(sl, player.position().add(0, 0.2, 0), 0.9, ParticleTypes.END_ROD, 16, 0.08);
            CeFx.burst(sl, player.position().add(0, 1, 0), CeFx.dust(0xFFF4C0, 1.0F), 16, 0.4, 0.0);
            sl.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, player.getSoundSource(), 1.0F, 1.6F);
            sl.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP, player.getSoundSource(), 0.8F, 1.6F);
            player.getCooldowns().addCooldown(this, 100);
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
