package com.renanpontes.isekaimahou.feature.mana_core.block;

import java.util.List;

import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.mana_core.ManaCoreRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** Item do Núcleo de Mana: mostra a carga guardada (tooltip + barrinha) e como usar. */
public class ManaCoreItem extends BlockItem {
    public ManaCoreItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static int stored(ItemStack stack) {
        return stack.getOrDefault(ManaCoreRegistries.CORE_MANA.get(), 0);
    }

    /** Núcleo cheio (aba criativa / comando). */
    public ItemStack full() {
        ItemStack s = new ItemStack(this);
        s.set(ManaCoreRegistries.CORE_MANA.get(), ManaCoreBlockEntity.CAPACITY);
        return s;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stored(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * stored(stack) / ManaCoreBlockEntity.CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return AutoFx.MANA_RGB;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.isekai_mahou.mana_core.stored", stored(stack), ManaCoreBlockEntity.CAPACITY)
                .withColor(AutoFx.MANA_RGB));
        for (int i = 1; i <= 3; i++) {
            tooltip.add(Component.translatable("block.isekai_mahou.mana_core.tooltip" + i)
                    .withStyle(i == 1 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        }
    }
}
