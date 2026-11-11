package com.renanpontes.isekaimahou.feature.guild.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Moeda da Guilda: recompensa das missões; gasta na loja da Guilda e nas trocas com aventureiros. */
public class GuildCoinItem extends Item {
    public GuildCoinItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.isekai_mahou.guild_coin.desc").withStyle(ChatFormatting.GRAY));
    }
}
