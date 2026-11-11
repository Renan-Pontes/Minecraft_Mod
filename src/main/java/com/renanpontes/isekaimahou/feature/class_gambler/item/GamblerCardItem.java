package com.renanpontes.isekaimahou.feature.class_gambler.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.ThrownCardEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Carta de Baralho: arremessável (gasta 1; 3 de dano). Também é o visual das cartas da forma Carta. */
public class GamblerCardItem extends Item {
    private static final float DAMAGE = 3F;

    public GamblerCardItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl) {
            ThrownCardEntity card = ThrownCardEntity.physical(level, player, DAMAGE, 0xE8E2F4);
            card.shootFromRotation(player, player.getXRot(), player.getYRot(), 0F, 1.8F, 0.8F);
            sl.addFreshEntity(card);
            GbFx.sound(sl, player.getEyePosition(), SoundEvents.BOOK_PAGE_TURN, 0.7F, 1.9F);
        }
        stack.consume(1, player);
        player.getCooldowns().addCooldown(this, 6);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gambler_card").withStyle(ChatFormatting.GRAY));
    }
}
