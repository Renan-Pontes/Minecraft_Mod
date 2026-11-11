package com.renanpontes.isekaimahou.feature.automation.block;

import java.util.List;

import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.automation.AutoRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** Item da Bateria: mostra a carga guardada (tooltip + barrinha como a de durabilidade). */
public class ManaBatteryItem extends BlockItem {
    public ManaBatteryItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static int stored(ItemStack stack) {
        return stack.getOrDefault(AutoRegistries.BATTERY_MANA.get(), 0);
    }

    /** Bateria cheia (aba criativa). */
    public ItemStack full() {
        ItemStack s = new ItemStack(this);
        s.set(AutoRegistries.BATTERY_MANA.get(), ManaBatteryBlockEntity.CAPACITY);
        return s;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return stored(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * stored(stack) / ManaBatteryBlockEntity.CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return AutoFx.MANA_RGB;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.isekai_mahou.mana_battery.stored", stored(stack), ManaBatteryBlockEntity.CAPACITY)
                .withColor(AutoFx.MANA_RGB));
        tooltip.add(Component.translatable("block.isekai_mahou.mana_battery.tooltip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.isekai_mahou.mana_battery.tooltip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
