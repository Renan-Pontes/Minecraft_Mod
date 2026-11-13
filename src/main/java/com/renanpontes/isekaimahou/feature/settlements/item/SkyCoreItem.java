package com.renanpontes.isekaimahou.feature.settlements.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Núcleo Celeste (onda 7, sky_island): o custo de fundar a cidade numa Ilha Celeste (SkyLand). Não se usa sozinho —
 * basta estar no inventário junto da Carta de Fundação; a fundação no céu consome um ({@code SettlementService.found}).
 */
public class SkyCoreItem extends Item {
    public SkyCoreItem(Properties props) {
        super(props);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.isekai_mahou.sky_core.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.isekai_mahou.sky_core.use").withStyle(ChatFormatting.AQUA));
    }
}
