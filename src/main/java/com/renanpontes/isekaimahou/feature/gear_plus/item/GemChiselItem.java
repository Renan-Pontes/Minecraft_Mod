package com.renanpontes.isekaimahou.feature.gear_plus.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.gear_plus.Gems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Cinzel de Lapidário: com ele no cursor, o botão DIREITO num equipamento com gemas tira a ÚLTIMA gema, inteira.
 * Qualquer classe usa (quem herdou um item com gemas pode recuperá-las).
 */
public class GemChiselItem extends Item {
    public GemChiselItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack chisel, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY) return false;
        ItemStack target = slot.getItem();
        if (target.isEmpty() || !Gems.hasGems(target) || !slot.allowModification(player)) return false;
        ItemStack gem = Gems.extractLast(target);
        slot.setChanged();
        if (!gem.isEmpty()) player.getInventory().placeItemBackInInventory(gem);
        if (player instanceof ServerPlayer sp && sp.level() instanceof ServerLevel level) {
            chisel.hurtAndBreak(1, level, sp, item -> { });
            level.playSound(null, sp.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.6F, 1.5F);
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.gear_plus.chisel").withStyle(ChatFormatting.GRAY));
    }
}
