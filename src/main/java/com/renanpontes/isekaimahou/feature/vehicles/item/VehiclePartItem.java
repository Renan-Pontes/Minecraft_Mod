package com.renanpontes.isekaimahou.feature.vehicles.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.vehicles.VehicleUpgrades;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Onda 10: peça de melhoria de veículo (Motor Rúnico, Blindagem Arcana, Porão Expandido), vendida pelo Mecânico.
 * Clique no SEU veículo com a peça para instalar (cada peça sobe 1 nível, até {@link VehicleUpgrades#MAX}); a melhoria
 * fica no veículo, inclusive quando ele é guardado como item. Quem aplica é {@code MagicVehicle.interact}.
 */
public class VehiclePartItem extends Item {
    private final VehicleUpgrades.Part part;

    public VehiclePartItem(VehicleUpgrades.Part part, Properties props) {
        super(props);
        this.part = part;
    }

    public VehicleUpgrades.Part part() {
        return part;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.isekai_mahou.vehicle_part.use", VehicleUpgrades.MAX).withStyle(ChatFormatting.DARK_AQUA));
    }
}
