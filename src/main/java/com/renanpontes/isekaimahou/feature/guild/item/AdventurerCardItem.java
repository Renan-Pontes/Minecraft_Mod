package com.renanpontes.isekaimahou.feature.guild.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.guild.GuildContent;
import com.renanpontes.isekaimahou.feature.guild.GuildData;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork;
import com.renanpontes.isekaimahou.feature.guild.GuildRank;
import com.renanpontes.isekaimahou.feature.guild.GuildService;

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

/**
 * Cartão de Aventureiro: recebido na primeira conversa com a recepcionista. Mostra nome, rank, pontos e
 * missões concluídas no tooltip (o servidor mantém atualizado). Clique direito abre a aba "Guilda" do menu.
 * A cor do cartão muda com o rank (propriedade de modelo "isekai_mahou:rank").
 */
public class AdventurerCardItem extends Item {
    public AdventurerCardItem(Properties properties) {
        super(properties);
    }

    /** Cartão novo para o jogador, com os dados atuais. */
    public static ItemStack issue(ServerPlayer player, GuildData d) {
        ItemStack stack = new ItemStack(GuildContent.ADVENTURER_CARD.get());
        stack.set(GuildContent.CARD.get(), infoOf(player, d));
        return stack;
    }

    public static GuildCardInfo infoOf(ServerPlayer player, GuildData d) {
        return new GuildCardInfo(player.getUUID(), player.getGameProfile().getName(), Math.max(1, d.rankLevel()), d.points(), d.completed());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        GuildCardInfo info = stack.get(GuildContent.CARD.get());
        if (level.isClientSide) {
            if (info != null && info.owner().equals(player.getUUID())) GuildNetwork.openGuildTab.run();
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof ServerPlayer sp) {
            GuildData d = GuildService.data(sp);
            if (info == null && d.registered()) {
                // Cartão em branco nas mãos de um membro: passa a ser dele.
                stack.set(GuildContent.CARD.get(), infoOf(sp, d));
                sp.playNotifySound(SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.guild.card_bound").withStyle(ChatFormatting.GOLD), true);
            } else if (info == null) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.guild.card_blank").withStyle(ChatFormatting.GRAY), true);
            } else if (!info.owner().equals(sp.getUUID())) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.guild.card_other", info.name())
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        GuildCardInfo info = stack.get(GuildContent.CARD.get());
        if (info == null) {
            tooltip.add(Component.translatable("item.isekai_mahou.adventurer_card.blank").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            return;
        }
        GuildRank found = GuildRank.byLevel(info.rank());
        final GuildRank rank = found == null ? GuildRank.F : found;
        tooltip.add(Component.translatable("item.isekai_mahou.adventurer_card.name", Component.literal(info.name()).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.isekai_mahou.adventurer_card.rank",
                rank.letter().withStyle(s -> s.withColor(rank.rgb()).withBold(true))).withStyle(ChatFormatting.GRAY));
        GuildRank next = rank.next();
        if (next != null) {
            tooltip.add(Component.translatable("item.isekai_mahou.adventurer_card.points_next", info.points(), next.pointsRequired)
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.isekai_mahou.adventurer_card.points", info.points()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.isekai_mahou.adventurer_card.completed", info.completed()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.isekai_mahou.adventurer_card.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
