package com.renanpontes.isekaimahou.feature.armory.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.armory.WeaponKind;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Cajado do Mago, Varinha da Bruxa e Cajado de Galhos do Druida: armas de foco mágico (sem dano extra corpo a corpo).
 * Os bônus mágicos estão no componente de atributos; a passiva do tier IV fica em {@code ArmoryPassives}.
 */
public class ArcaneWeaponItem extends Item implements ArmoryWeapon {
    private final WeaponKind kind;
    private final int tier;

    public ArcaneWeaponItem(WeaponKind kind, int tier, Properties props) {
        super(props.stacksTo(1).attributes(ArmoryWeapon.attributes(kind, tier)));
        this.kind = kind;
        this.tier = tier;
    }

    @Override
    public WeaponKind kind() {
        return kind;
    }

    @Override
    public int tier() {
        return tier;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ArmoryWeapon.appendTooltip(this, getDescriptionId() + ".desc", tooltip);
    }
}
