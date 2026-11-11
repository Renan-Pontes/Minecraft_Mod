package com.renanpontes.isekaimahou.feature.class_knight.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_knight.KnightSwords;
import com.renanpontes.isekaimahou.feature.class_knight.RuneData;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Lâmina Rúnica — a arma do kit do Cavaleiro Rúnico ({@code runic_blade}) e seus graus {@code runic_blade_t1..t4}.
 * Espada com sulco de runas: guarda magias inteiras gravadas pela forma "Gravar na Lâmina" (1 por espaço; grau I 2
 * espaços até 5 no IV) e cada golpe principal solta a próxima runa. Agachar + botão direito apaga as runas.
 */
public class RunicBladeItem extends SwordItem {
    private final int tierNumber;

    public RunicBladeItem(Tier tier, int tierNumber, Properties props) {
        super(tier, props);
        this.tierNumber = tierNumber;
    }

    /** Grau da lâmina (1..4). */
    public int tierNumber() {
        return tierNumber;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return !KnightSwords.runes(stack).isEmpty() || super.isFoil(stack);
    }

    /** Gravar/apagar runas muda o item: só re-equipa na mão se trocou de item ou de espaço. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && !KnightSwords.runes(stack).isEmpty()) {
            if (!level.isClientSide) clearRunes((ServerPlayer) player, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    /** Apaga as runas com um sopro de faíscas (usado também pelas Lendárias). */
    public static void clearRunes(ServerPlayer player, ItemStack stack) {
        KnightSwords.setRunes(stack, RuneData.EMPTY);
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.4, 0.4, 0.4, 0.6);
        player.displayClientMessage(Component.translatable("message.isekai_mahou.class_knight.runes_cleared")
                .withStyle(ChatFormatting.GRAY), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(com.renanpontes.isekaimahou.feature.class_knight.KnightText.tr("tooltip", "runic_blade", "lore", tierNumber)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        KnightSwords.runeTooltip(stack, KnightSwords.baseSlots(stack), tooltip);
        if (flag.hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.rune_how").withStyle(ChatFormatting.DARK_AQUA));
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.rune_cost",
                    Math.round(KnightSwords.procCost(stack) * 100)).withStyle(ChatFormatting.DARK_GRAY));
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.rune_oath").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.isekai_mahou.class_knight.more").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
