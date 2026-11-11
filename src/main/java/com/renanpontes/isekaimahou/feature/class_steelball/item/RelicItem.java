package com.renanpontes.isekaimahou.feature.class_steelball.item;

import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_steelball.SbFx;
import com.renanpontes.isekaimahou.feature.class_steelball.SbPart;
import com.renanpontes.isekaimahou.feature.class_steelball.SbRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
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
 * Relicário Selado: o que se acha nos baús (Sepulturas do Santo e, raramente, outras estruturas). Aberto, revela uma
 * Parte do Cadáver Santo que o jogador AINDA NÃO TEM (com todas, uma qualquer — para dar a um amigo). Assim ninguém
 * fica preso a partes repetidas. O Coração (a última parte) nunca sai daqui: ele bate dentro de um chefe.
 */
public class RelicItem extends Item {
    public RelicItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            // o Coração (parte final) nunca está num relicário: com as 8 comuns, sai uma repetida (para um amigo)
            List<SbPart> missing = SbPart.relicPool(MagicApi.get(sp));
            SbPart part = missing.isEmpty() ? SbPart.values()[sp.getRandom().nextInt(SbPart.values().length - 1)]
                    : missing.get(sp.getRandom().nextInt(missing.size()));
            if (missing.isEmpty() && !SbPart.FINAL.owned(MagicApi.get(sp))) {
                sp.sendSystemMessage(Component.translatable("message.isekai_mahou.steelball.heart_hint")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
            }
            if (!sp.getAbilities().instabuild) stack.shrink(1);
            Rewards.give(sp, new ItemStack(SbRegistry.PARTS.get(part).get()));
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.relic_opened", part.displayName())
                    .withStyle(ChatFormatting.GOLD), true);
            SbFx.sound(sp.serverLevel(), sp.position(), SoundEvents.CHEST_OPEN, 0.8F, 0.7F);
            SbFx.sound(sp.serverLevel(), sp.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.8F);
            sp.serverLevel().sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.2, sp.getZ(), 16, 0.3, 0.3, 0.3, 0.05);
            SbFx.spinRing(sp.serverLevel(), sp.position().add(0, 1.0, 0), part.rgb, 0.8, 14);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.relic").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.steelball.relic.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
