package com.renanpontes.isekaimahou.feature.hueco_mundo.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.hueco_mundo.HmTravel;

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
 * Chave da Garganta: máscara de Vazio partida que ainda lembra o caminho de casa. Usar rasga uma Garganta à sua frente
 * (no Mundo Normal leva ao Mundo Oco; dentro dele, de volta). Gasta a chave. Feita com fragmentos de máscara e núcleo
 * de Menos — ou dada pelo Rei Vazio.
 */
public class GargantaKeyItem extends Item {

    public GargantaKeyItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        if (HmTravel.shortcut(sp, HmTravel.spotAhead(sp), false)) {
            stack.consume(1, sp);
            sp.getCooldowns().addCooldown(this, 100);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.isekai_mahou.garganta_key.desc").withStyle(ChatFormatting.GRAY));
    }
}
