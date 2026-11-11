package com.renanpontes.isekaimahou.feature.farm.machine;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Block;

/** Item do Pedestal Elemental: o nome mostra o elemento ("Pedestal Elemental de Fogo"). */
public class ElementalPedestalItem extends BlockItem {
    public ElementalPedestalItem(Block block, Properties props) {
        super(block, props);
    }

    public static Element element(ItemStack stack) {
        BlockItemStateProperties props = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        Element e = props.get(ElementalPedestalBlock.ELEMENT);
        return e == null ? Element.FIRE : e;
    }

    @Override
    public Component getName(ItemStack stack) {
        Element e = element(stack);
        return Component.translatable("block.isekai_mahou.elemental_pedestal.of", e.displayName()).withColor(e.color);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        Element e = element(stack);
        tooltip.add(Component.translatable("farm.isekai_mahou.machine.elemental_pedestal.hint", e.displayName()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("farm.isekai_mahou.machine.input_line",
                Component.translatable("farm.isekai_mahou.machine.elemental_pedestal.input")).withColor(e.color));
        tooltip.add(Component.translatable("farm.isekai_mahou.machine.mana_line").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("farm.isekai_mahou.machine.elemental_pedestal.origin").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
