package com.renanpontes.isekaimahou.feature.class_quincy.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyTechniques;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Luva Sanrei (homenagem à luva de treino Quincy): segurada em qualquer mão, PESA no Reishi — as técnicas Quincy saem
 * com 70% do poder —, mas cada inimigo derrotado com elas conta para o treino. 150 abates no rank V liberam a
 * <b>Quincy: Letzt Stil</b>. Vem no rank IV do Auswählen (ou na bancada).
 */
public class SanreiGloveItem extends Item {
    public SanreiGloveItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.isekai_mahou.quincy_sanrei_glove.1").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.isekai_mahou.quincy_sanrei_glove.2").withStyle(ChatFormatting.DARK_AQUA));
        var d = ClientMagic.data();
        if (d != null) {
            int v = Math.min(QuincyTechniques.SANREI_GOAL, Math.max(0, d.counter(Quincy.SANREI)));
            lines.add(Component.translatable("tooltip.isekai_mahou.quincy_sanrei_glove.progress", v, QuincyTechniques.SANREI_GOAL)
                    .withColor(0xFFF2C0));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
