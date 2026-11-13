package com.renanpontes.isekaimahou.feature.dwarf_hold.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Peça da armadura de Mithril: só acrescenta a dica do bônus de conjunto (aplicado em {@code DwarfHoldEvents}). */
public class MithrilArmorItem extends ArmorItem {
    public MithrilArmorItem(Holder<ArmorMaterial> material, Type type, Properties props) {
        super(material, type, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.dwarf_hold.mithril_set").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.dwarf_hold.mithril_set.desc").withStyle(ChatFormatting.GRAY));
    }
}
