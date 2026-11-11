package com.renanpontes.isekaimahou.feature.class_steelball.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_steelball.SbMentorService;

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
 * Carta do Mestre (kit): o velho mestre itinerante aparece por conta própria quando há lição pronta, mas a carta o chama
 * a qualquer hora (chega em alguns segundos). Recarga de 1 min.
 */
public class MentorLetterItem extends Item {
    public static final int COOLDOWN = 1200;

    public MentorLetterItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp && SbMentorService.callByLetter(sp)) {
            sp.getCooldowns().addCooldown(this, COOLDOWN);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.letter").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.letter.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
