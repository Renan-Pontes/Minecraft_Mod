package com.renanpontes.isekaimahou.feature.automation.block;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** Item de bloco da Automação com dicas no tooltip ("block.isekai_mahou.<id>.tooltip1..N"). */
public class AutomationBlockItem extends BlockItem {
    private final int lines;

    public AutomationBlockItem(Block block, Properties properties, int lines) {
        super(block, properties);
        this.lines = lines;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 1; i <= lines; i++) {
            String key = getDescriptionId() + ".tooltip" + i;
            tooltip.add(Component.translatable(key).withStyle(i == 1 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        }
    }
}
