package com.renanpontes.isekaimahou.feature.class_rogue.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_rogue.entity.RogueKnifeEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Faca de Arremesso: arremessa uma faca (4 de dano; as passivas do Ladino valem — pelas costas ×2 e crítico). Cai no
 * chão 70% das vezes para ser recolhida. 0,4 s entre arremessos.
 */
public class ThrowingKnifeItem extends Item {
    public ThrowingKnifeItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS,
                0.6F, 1.6F + level.random.nextFloat() * 0.2F);
        if (!level.isClientSide) {
            RogueKnifeEntity knife = RogueKnifeEntity.thrown(level, player, stack);
            knife.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.8F, 0.6F);
            level.addFreshEntity(knife);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        player.getCooldowns().addCooldown(this, 8);
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.isekai_mahou.rogue_throwing_knife.desc").withStyle(ChatFormatting.GRAY));
    }
}
