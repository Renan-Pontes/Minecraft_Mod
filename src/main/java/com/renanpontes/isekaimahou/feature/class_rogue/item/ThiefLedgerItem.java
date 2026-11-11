package com.renanpontes.isekaimahou.feature.class_rogue.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_rogue.RogueRanks;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Livro-Razão do Ladrão: a progressão própria do Ladino (5 ranks, ver {@link RogueRanks}). Usar abre a aba "Ladino"
 * do menu B (rank, o que falta, páginas roubadas); agachado + usar tenta subir de rank (e lista o que falta no chat).
 */
public class ThiefLedgerItem extends Item {
    /** Preenchido pelo cliente: abre a aba "Ladino". */
    public static volatile Runnable openTab = () -> {};

    public ThiefLedgerItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer sp) RogueRanks.tryRankUp(sp, true);
        } else if (level.isClientSide) {
            openTab.run();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.isekai_mahou.rogue_ledger.flavor").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.isekai_mahou.rogue_ledger.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
