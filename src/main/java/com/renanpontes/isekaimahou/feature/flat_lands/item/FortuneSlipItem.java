package com.renanpontes.isekaimahou.feature.flat_lands.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.flat_lands.FortuneSlips;
import com.renanpontes.isekaimahou.feature.flat_lands.Omikuji;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Papel da Sorte (Omikuji): lembrança da sorte do dia. Papel de azar se amarra na caixa do santuário. */
public class FortuneSlipItem extends Item {
    public FortuneSlipItem(Properties props) {
        super(props);
    }

    @Override
    public Component getName(ItemStack stack) {
        Omikuji.Fortune f = FortuneSlips.fortune(stack);
        if (f == null) return super.getName(stack);
        return Component.translatable("item.isekai_mahou.sakura_fortune_slip.named", f.title());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        Omikuji.Fortune f = FortuneSlips.fortune(stack);
        if (f == null) {
            tooltip.add(Component.translatable("item.isekai_mahou.sakura_fortune_slip.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(f.reading().copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable(f.isCurse() ? "item.isekai_mahou.sakura_fortune_slip.curse"
                : "item.isekai_mahou.sakura_fortune_slip.keep").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return FortuneSlips.fortune(stack) == Omikuji.Fortune.DAIKICHI;
    }
}
