package com.renanpontes.isekaimahou.feature.class_knight.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_knight.KnightSwords;
import com.renanpontes.isekaimahou.feature.class_knight.LegendSword;
import com.renanpontes.isekaimahou.feature.class_knight.SwordAbilities;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Espada Mágica Lendária ({@link LegendSword}): lâmina de 4 espaços de runa, golpe rúnico mais forte e barato, eco do
 * elemento nos golpes, passiva enquanto empunhada e a LIBERAÇÃO no botão direito (recarga de 30 s, custa 20% do
 * recurso máximo). Agachar + botão direito apaga as runas.
 */
public class LegendarySwordItem extends SwordItem {
    /** Recarga da Liberação (ticks). */
    public static final int RELEASE_COOLDOWN = 600;

    private final LegendSword legend;

    public LegendarySwordItem(LegendSword legend, Tier tier, Properties props) {
        super(tier, props);
        this.legend = legend;
    }

    public LegendSword legend() {
        return legend;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /** Gravar/apagar runas muda o item: só re-equipa na mão se trocou de item ou de espaço. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (KnightSwords.runes(stack).isEmpty()) return InteractionResultHolder.pass(stack);
            if (!level.isClientSide) RunicBladeItem.clearRunes((ServerPlayer) player, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide && SwordAbilities.release((ServerPlayer) player, legend)) {
            player.getCooldowns().addCooldown(this, RELEASE_COOLDOWN);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(com.renanpontes.isekaimahou.feature.class_knight.KnightText.tr("tooltip", legend.key(), "lore")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.legend_element", legend.element.displayName())
                .withColor(legend.rgb));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.legend_release", legend.abilityName())
                .withStyle(ChatFormatting.GOLD));
        if (flag.hasShiftDown()) {
            tooltip.add(legend.abilityDesc().withStyle(ChatFormatting.YELLOW));
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.legend_passive", legend.passiveDesc())
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.legend_echo", legend.element.displayName())
                    .withStyle(ChatFormatting.DARK_AQUA));
            if (legend == LegendSword.LAKE) {
                tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.lake_excalibur").withStyle(ChatFormatting.GOLD));
            }
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.more").withStyle(ChatFormatting.DARK_GRAY));
        }
        KnightSwords.runeTooltip(stack, KnightSwords.baseSlots(stack), tooltip);
    }
}
