package com.renanpontes.isekaimahou.feature.bosses_f.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Item deixado por um chefe do Overworld (troféu ou material). Sem uso próprio: é pedido pela Ascensão, pelas receitas
 * e trocas de outros módulos (por id). Dica com a origem e o uso.
 */
public class BossTrophyItem extends Item {
    private final boolean trophy;

    public BossTrophyItem(Properties props, boolean trophy) {
        super(props);
        this.trophy = trophy;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return trophy || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable(trophy ? "item.isekai_mahou.bosses_f.trophy_hint" : "item.isekai_mahou.bosses_f.material_hint")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
