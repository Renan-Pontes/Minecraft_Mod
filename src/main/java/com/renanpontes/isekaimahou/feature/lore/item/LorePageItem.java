package com.renanpontes.isekaimahou.feature.lore.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.lore.LoreContent;
import com.renanpontes.isekaimahou.feature.lore.LoreFragments;
import com.renanpontes.isekaimahou.feature.lore.LoreService;

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
 * <b>Página Perdida</b>: usar lê a página. Sem o componente {@link LoreContent#FRAGMENT} ela é "em branco para o
 * destino": ao ler, vira o próximo fragmento que o jogador ainda não tem. Quem já tem tudo (ou já conhece o fragmento
 * escrito nela) fica com a página — dá para entregar a um amigo.
 */
public class LorePageItem extends Item {
    public LorePageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) LoreService.readPage(sp, stack);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        LoreFragments.Fragment f = LoreFragments.get(stack.get(LoreContent.FRAGMENT.get()));
        if (f != null) {
            LoreFragments.LoreSet set = LoreFragments.set(f.set());
            tooltip.add(f.title().withStyle(ChatFormatting.GOLD));
            if (set != null) tooltip.add(set.title().withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("item.isekai_mahou.lore_page.desc").withStyle(ChatFormatting.GRAY));
    }
}
