package com.renanpontes.isekaimahou.feature.depths.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Item simples com descrição ({@code item.isekai_mahou.<id>.desc}) e uso ({@code .use}) no tooltip. */
public class DepthsLoreItem extends Item {
    private final boolean glint;

    public DepthsLoreItem(Properties props, boolean glint) {
        super(props);
        this.glint = glint;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return glint || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lore(stack, lines);
    }

    /** Linhas de descrição/uso pelo id do item (compartilhado com as armas do módulo). */
    public static void lore(ItemStack stack, List<Component> lines) {
        String base = stack.getDescriptionId();
        lines.add(Component.translatable(base + ".desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable(base + ".use").withStyle(ChatFormatting.GOLD));
    }
}
