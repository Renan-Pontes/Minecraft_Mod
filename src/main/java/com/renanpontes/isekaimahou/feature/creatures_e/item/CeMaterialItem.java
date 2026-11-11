package com.renanpontes.isekaimahou.feature.creatures_e.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Material deixado pelas criaturas das dimensoes; descricao em "item.isekai_mahou.&lt;id&gt;.desc". */
public class CeMaterialItem extends Item {
    private final boolean shiny;

    public CeMaterialItem(Properties props, boolean shiny) {
        super(props);
        this.shiny = shiny;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return shiny || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
