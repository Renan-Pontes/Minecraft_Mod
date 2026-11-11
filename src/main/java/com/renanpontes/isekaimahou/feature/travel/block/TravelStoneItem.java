package com.renanpontes.isekaimahou.feature.travel.block;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** Item da Pedra de Teleporte: explica como usar e o nome que o lugar vai ter. */
public class TravelStoneItem extends BlockItem {
    public TravelStoneItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tooltip, flag);
        tooltip.add(Component.translatable("item.isekai_mahou.travel_stone.tip1").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.isekai_mahou.travel_stone.tip2").withStyle(ChatFormatting.GRAY));
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        tooltip.add(name != null
                ? Component.translatable("item.isekai_mahou.travel_stone.named", name).withStyle(ChatFormatting.GOLD)
                : Component.translatable("item.isekai_mahou.travel_stone.unnamed").withStyle(ChatFormatting.DARK_GRAY));
    }
}
