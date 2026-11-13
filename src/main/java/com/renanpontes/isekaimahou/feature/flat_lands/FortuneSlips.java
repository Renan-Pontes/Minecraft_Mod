package com.renanpontes.isekaimahou.feature.flat_lands;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** O papel do Omikuji guarda qual sorte saiu no CUSTOM_DATA ({@code {"sakura_fortune": "<id>"}}). */
public final class FortuneSlips {
    private static final String KEY = "sakura_fortune";

    public static ItemStack create(Omikuji.Fortune f) {
        ItemStack stack = new ItemStack(FlatLandsContent.FORTUNE_SLIP.get());
        CompoundTag t = new CompoundTag();
        t.putString(KEY, f.id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        return stack;
    }

    /** A sorte escrita no papel (null = papel em branco, ex.: o da aba criativa). */
    public static Omikuji.Fortune fortune(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag t = data.copyTag();
        return t.contains(KEY) ? Omikuji.Fortune.byId(t.getString(KEY)) : null;
    }

    private FortuneSlips() {}
}
