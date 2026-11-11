package com.renanpontes.isekaimahou.feature.ritual_mage.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.ritual_mage.MageClientBridge;
import com.renanpontes.isekaimahou.feature.ritual_mage.network.ClientResearch;
import com.renanpontes.isekaimahou.feature.ritual_mage.network.MagePayloads;
import com.renanpontes.isekaimahou.feature.ritual_mage.research.Thesis;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Atlas Arcano: abre a tela com os fenômenos observados e as teses. */
public class MageAtlasItem extends Item {
    public MageAtlasItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 1.0F);
        if (level.isClientSide) {
            MageClientBridge.openAtlas.run();
        } else if (player instanceof ServerPlayer sp) {
            // Garante que o cliente tem a versão mais nova do Atlas antes de desenhar
            MagePayloads.syncAtlas(sp);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        // Tooltip só é montada no cliente; ClientResearch é uma classe comum (vazia no servidor).
        tooltip.add(Component.translatable("item.isekai_mahou.mage_atlas.tooltip", ClientResearch.discoveredCount(),
                ClientResearch.phenomena().size()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.isekai_mahou.mage_atlas.tooltip2", ClientResearch.thesisCount(),
                Thesis.ALL.size()).withStyle(ChatFormatting.GRAY));
    }
}
