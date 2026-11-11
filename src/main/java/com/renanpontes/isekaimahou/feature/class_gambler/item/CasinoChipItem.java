package com.renanpontes.isekaimahou.feature.class_gambler.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Ficha de Cassino: cai dos Jackpots (e às vezes de inimigos abatidos por um Apostador). É a APOSTA da Rodada da Casa
 * (subir de rank). Clique direito: joga cara ou coroa (só por diversão, não gasta).
 */
public class CasinoChipItem extends Item {
    public CasinoChipItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl) {
            boolean heads = sl.random.nextBoolean();
            player.displayClientMessage(Component.translatable(heads ? "message.isekai_mahou.gambler.flip_heads"
                    : "message.isekai_mahou.gambler.flip_tails").withStyle(heads ? ChatFormatting.GOLD : ChatFormatting.DARK_PURPLE), true);
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.copyWithCount(1)), player.getX(), player.getEyeY() + 0.4,
                    player.getZ(), 4, 0.05, 0.1, 0.05, 0.05);
            GbFx.sound(sl, player.getEyePosition(), SoundEvents.CHAIN_PLACE, 0.6F, heads ? 1.9F : 1.5F);
        }
        player.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.casino_chip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.casino_chip.flip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
