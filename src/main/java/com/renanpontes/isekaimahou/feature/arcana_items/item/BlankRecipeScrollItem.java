package com.renanpontes.isekaimahou.feature.arcana_items.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.arcana_items.ArcanaHooks;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Pergaminho de Receita em Branco: caro de fazer (materiais raros). Botão direito abre a lista das magias guardadas
 * na biblioteca do Grimório; gravar uma custa níveis de experiência e mana (ver {@code RecipeScrolls}).
 */
public class BlankRecipeScrollItem extends Item {
    public BlankRecipeScrollItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) ArcanaHooks.openInscribe.accept(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.arcana_items.blank_scroll.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.arcana_items.blank_scroll.cost").withStyle(ChatFormatting.DARK_GRAY));
    }
}
