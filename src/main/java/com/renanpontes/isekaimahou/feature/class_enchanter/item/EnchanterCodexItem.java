package com.renanpontes.isekaimahou.feature.class_enchanter.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_enchanter.rite.EnchanterRite;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;

/**
 * Códex de Inscrição: o livro do Encantador. Usar abre a aba "Encantador" (ranks, tarefas, oferenda); usar NUMA mesa de
 * encantamento começa a Inscrição do próximo rank (se tudo estiver pronto).
 */
public class EnchanterCodexItem extends Item {
    /** Gancho de cliente: abre a aba do Encantador no menu B (o cliente substitui). */
    public static volatile Runnable openScreen = () -> {};

    public EnchanterCodexItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) openScreen.run();
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level.getBlockState(ctx.getClickedPos()).getBlock() instanceof EnchantingTableBlock)) return InteractionResult.PASS;
        if (!level.isClientSide && ctx.getPlayer() instanceof ServerPlayer sp) EnchanterRite.tryStart(sp, ctx.getClickedPos());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.isekai_mahou.enchanter_codex.desc").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.enchanter_codex.use").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.enchanter_codex.rite").withStyle(ChatFormatting.DARK_AQUA));
    }
}
