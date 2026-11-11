package com.renanpontes.isekaimahou.feature.class_quincy.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Ampola de Prata (homenagem ao Gintō): tubo com Reishi líquido. Uma magia com o aumento "Ampola de Prata" gasta uma
 * do inventário ao ser conjurada e sai mais forte (ver QuincyEvents). Feita com pepitas de ferro, vidro e ametista.
 */
public class SilverTubeItem extends Item {
    public SilverTubeItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.quincy_silver_tube").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.quincy_silver_tube.use").withColor(Quincy.RGB));
    }
}
