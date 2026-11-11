package com.renanpontes.isekaimahou.client.classes;

import com.renanpontes.isekaimahou.core.item.SoulBinding;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Linha na dica dos itens de classe: "Vinculado a X — intransferível" (ou que vai vincular a quem pegar primeiro). */
public final class BoundTooltip {
    public static void onTooltip(ItemTooltipEvent e) {
        if (!SoulBinding.isClassItem(e.getItemStack())) return;
        String name = SoulBinding.ownerName(e.getItemStack());
        e.getToolTip().add(name.isEmpty()
                ? Component.translatable("tooltip.isekai_mahou.bindable").withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable("tooltip.isekai_mahou.bound", name).withStyle(ChatFormatting.GOLD));
    }

    private BoundTooltip() {}
}
