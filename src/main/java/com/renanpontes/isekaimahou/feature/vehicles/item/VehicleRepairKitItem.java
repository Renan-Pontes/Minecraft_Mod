package com.renanpontes.isekaimahou.feature.vehicles.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.vehicles.entity.MagicVehicle;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Onda 10: Kit de Reparos — clique num veículo para devolver 40% da vida dele (qualquer um pode ajudar a consertar).
 * O conserto completo, sem gastar kits, é com o Mecânico. Quem aplica é {@code MagicVehicle.interact}.
 */
public class VehicleRepairKitItem extends Item {
    public VehicleRepairKitItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc", Math.round(MagicVehicle.KIT_REPAIR * 100))
                .withStyle(ChatFormatting.GRAY));
    }
}
