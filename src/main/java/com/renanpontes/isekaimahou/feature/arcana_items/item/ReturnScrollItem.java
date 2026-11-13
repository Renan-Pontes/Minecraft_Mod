package com.renanpontes.isekaimahou.feature.arcana_items.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.arcana_items.ReturnTravel;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Pergaminho de Retorno: consumível barato. Segure o botão direito por 3 s (fora de combate) para voltar à última
 * cidade visitada. Levar dano de um inimigo durante a leitura interrompe.
 */
public class ReturnScrollItem extends Item {
    private static final int READ_TICKS = 60;

    public ReturnScrollItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            ReturnTravel.Block b = ReturnTravel.check(sp);
            if (b != null) {
                fail(sp, b);
                return InteractionResultHolder.fail(stack);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof ServerPlayer sp)) return;
        if (ReturnTravel.inCombat(sp)) {
            sp.stopUsingItem();
            fail(sp, ReturnTravel.Block.IN_COMBAT);
            return;
        }
        if (remaining % 4 == 0) {
            sp.serverLevel().sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.6, sp.getZ(), 4, 0.4, 0.3, 0.4, 0.6);
        }
        if (remaining % 20 == 0) {
            sp.serverLevel().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6F, 1.2F);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer sp) {
            ReturnTravel.Block b = ReturnTravel.travel(sp);
            if (b != null) {
                fail(sp, b);
                return stack;
            }
            stack.consume(1, sp);
            sp.getCooldowns().addCooldown(this, 100);
        }
        return stack;
    }

    private static void fail(ServerPlayer sp, ReturnTravel.Block b) {
        sp.displayClientMessage(ReturnTravel.message(b, sp).copy().withStyle(ChatFormatting.RED), true);
        sp.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6F, 0.6F);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return READ_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.arcana_items.return_scroll.desc").withStyle(ChatFormatting.GRAY));
    }
}
