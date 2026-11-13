package com.renanpontes.isekaimahou.feature.settlements.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.settlements.SettlementService;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Carta de Fundação: usada no chão, abre a escolha do estilo (Reino / Desértico / Nórdico) e do nome. Ao confirmar,
 * o Coração da Cidade nasce no lugar e o primeiro construtor chega (a carta é consumida).
 * <p>v1.0 (onda 4): com a Carta na mão principal o cliente mostra a PRÉVIA da cidade ({@code client/CityPlacer}) e é
 * ele quem trata o clique (fixar, mover, girar, confirmar). O {@link #useOn} daqui continua valendo para a Carta na
 * outra mão: abre a fundação direto no bloco clicado, como sempre foi.
 */
public class FoundingCharterItem extends Item {
    public FoundingCharterItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (ctx.getPlayer() instanceof ServerPlayer sp) SettlementService.openFounding(sp, ctx.getClickedPos().above());
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.isekai_mahou.founding_charter.desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.isekai_mahou.founding_charter.use").withStyle(ChatFormatting.GOLD));
    }
}
