package com.renanpontes.isekaimahou.feature.ritual_witch.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Colher Ritual: clique direito no Caldeirão Ritual conta uma mexida (o caldeirão trata o clique). */
public class RitualSpoonItem extends Item {
    /** Intervalo entre mexidas (ticks): evita contar duas mexidas num clique só. */
    public static final int COOLDOWN = 10;

    public RitualSpoonItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ritual_spoon").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ritual_spoon.warn").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
