package com.renanpontes.isekaimahou.feature.home_realm;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Chave do Lar — "se comprar libera": usar (botão direito) liga o jogador ao seu terreno na Dimensão Casa e consome a
 * chave. Quem já tem um lar não gasta a chave (pode dar para um amigo).
 */
public class HomeKeyItem extends Item {
    public HomeKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(stack);
        if (player instanceof ServerPlayer sp && HomeManager.unlock(sp)) {
            stack.consume(1, player);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.isekai_mahou.home_key.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.isekai_mahou.home_key.desc2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
