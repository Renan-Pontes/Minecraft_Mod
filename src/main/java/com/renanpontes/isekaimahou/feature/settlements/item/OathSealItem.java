package com.renanpontes.isekaimahou.feature.settlements.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.settlements.Conquest;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Selo do Juramento: prova de que o jogador cumpriu o Juramento a uma cidade de reino. Usado no coração DAQUELA
 * cidade, invoca o Campeão da Cidade para o desafio final. Some ao vencer.
 */
public class OathSealItem extends Item {
    public OathSealItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (ctx.getPlayer() instanceof ServerPlayer sp) Conquest.useSealOn(sp, ctx.getClickedPos());
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.isekai_mahou.oath_seal.desc").withStyle(ChatFormatting.GRAY));
    }
}
