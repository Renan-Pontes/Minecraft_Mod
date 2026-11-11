package com.renanpontes.isekaimahou.feature.farm.machine;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** Item das fazendas mágicas: a dica explica o que entra e de onde vem a mana. */
public class FarmMachineItem extends BlockItem {
    private final MachineKind kind;

    public FarmMachineItem(Block block, MachineKind kind, Properties props) {
        super(block, props);
        this.kind = kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(kind.hint().withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("farm.isekai_mahou.machine.input_line", kind.inputHint()).withColor(kind.rgb));
        tooltip.add(Component.translatable("farm.isekai_mahou.machine.mana_line").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("farm.isekai_mahou.machine.output_line").withStyle(ChatFormatting.DARK_GRAY));
    }
}
