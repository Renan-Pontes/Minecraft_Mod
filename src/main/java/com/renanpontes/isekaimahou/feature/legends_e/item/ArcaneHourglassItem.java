package com.renanpontes.isekaimahou.feature.legends_e.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.feature.legends_e.SupremeCasting;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * Ampulheta Arcana: consumível caro. Enquanto estiver no inventário, a próxima Magia de Nível Supremo sai NA HORA
 * (sem os 8 s de círculos e sem entrar na Pilha — não pode ser contrariada); a ampulheta se desfaz. Usar (botão direito)
 * só mostra quantas Magias de Nível Supremo ainda restam hoje.
 */
public class ArcaneHourglassItem extends Item {
    public ArcaneHourglassItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            int max = SupremeCasting.maxUses(sp);
            int left = Math.max(0, max - MagicApi.dailyUses(sp, SupremeCasting.DAILY_KEY));
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_e.uses", left, max)
                    .withStyle(left > 0 ? ChatFormatting.AQUA : ChatFormatting.RED), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 1.4F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.isekai_mahou.arcane_hourglass.desc").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.isekai_mahou.arcane_hourglass.desc2").withStyle(ChatFormatting.GRAY));
    }
}
