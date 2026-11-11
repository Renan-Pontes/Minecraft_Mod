package com.renanpontes.isekaimahou.feature.ascension.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

/**
 * "Tributo do Círculo N" ({@code ascension_tribute_<n>}): um selo de mana condensada que vale como o TRIBUTO COMPLETO
 * da Ascensão ao Círculo N. O diário entrega nas missões "A Ascensão"; funciona em qualquer Núcleo — no inventário de
 * quem ativa ou num pedestal do círculo. Só vale para o Círculo escrito nele.
 */
public class AscensionTributeItem extends Item {
    /** Cores do selo por faixa de Círculo (igual às texturas geradas em tools/gen/ascension.py). */
    private static final int[] COLORS = {0, 0, 0x7FE3FF, 0x7FE3FF, 0x7FE3FF, 0xB98CFF, 0xB98CFF, 0xB98CFF, 0xFFD86B, 0xFFD86B, 0xFF7AC8};

    private final int circle;

    public AscensionTributeItem(int circle, Properties props) {
        super(props.stacksTo(16).fireResistant().rarity(rarityFor(circle)));
        this.circle = circle;
    }

    public static Rarity rarityFor(int circle) {
        if (circle >= 8) return Rarity.EPIC;
        if (circle >= 5) return Rarity.RARE;
        return Rarity.UNCOMMON;
    }

    /** Cor (RGB) do selo do Círculo informado. */
    public static int color(int circle) {
        return COLORS[Math.max(0, Math.min(COLORS.length - 1, circle))];
    }

    public int circle() {
        return circle;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ascension_tribute", circle).withColor(color(circle)));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ascension_tribute.use").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.ascension_tribute.only", circle).withStyle(ChatFormatting.DARK_GRAY));
    }
}
