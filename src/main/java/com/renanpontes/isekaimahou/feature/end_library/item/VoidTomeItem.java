package com.renanpontes.isekaimahou.feature.end_library.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.end_library.ElSpaceGlyphs;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Tomo Selado do Vazio: livro lacrado da Biblioteca. Ao abrir, o selo se rompe e sobra um Pergaminho de Glifo
 * ESPACIAL (de preferência um que você ainda não conhece). É a forma da Biblioteca entregar os pergaminhos do
 * space_magic sem depender dos ids dele nas tabelas de loot.
 */
public class VoidTomeItem extends Item {
    public VoidTomeItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl) {
            ItemStack scroll = ElSpaceGlyphs.randomScroll(sl.random, player);
            if (scroll.isEmpty()) {
                player.displayClientMessage(Component.translatable("item.isekai_mahou.void_tome.empty").withStyle(ChatFormatting.GRAY), true);
                return InteractionResultHolder.fail(stack);
            }
            if (!player.getInventory().add(scroll)) player.drop(scroll, false);
            sl.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.5, player.getZ(), 40, 0.5, 0.6, 0.5, 0.6);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.2, player.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
            sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
            sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.2F, 0.6F);
            player.displayClientMessage(Component.translatable("item.isekai_mahou.void_tome.opened", scroll.getHoverName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            MagicApi.get(player).setFlag("end_library.tome_read", true);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.isekai_mahou.void_tome.desc").withStyle(ChatFormatting.DARK_PURPLE));
        tip.add(Component.translatable("item.isekai_mahou.void_tome.use").withStyle(ChatFormatting.GRAY));
    }
}
