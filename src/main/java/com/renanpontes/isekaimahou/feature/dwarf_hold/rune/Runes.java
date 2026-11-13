package com.renanpontes.isekaimahou.feature.dwarf_hold.rune;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.dwarf_hold.DwarfRegistry;

import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;

/** Regras das runas: o que pode receber runa, em que espaço o bônus vale, e o evento que soma o bônus ao item. */
public final class Runes {
    /** Onde a runa vale e com qual bônus. */
    public record Target(Rune.Kind kind, EquipmentSlotGroup slot) {}

    @Nullable
    public static Rune of(ItemStack stack) {
        return stack.isEmpty() ? null : Rune.byId(stack.get(DwarfRegistry.RUNE.get()));
    }

    /** Peças que aceitam runa: armas corpo a corpo, armaduras, escudos e ferramentas (com durabilidade, uma por espaço). */
    @Nullable
    public static Target target(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxStackSize() > 1) return null;
        Item item = stack.getItem();
        if (item instanceof ArmorItem armor) return new Target(Rune.Kind.ARMOR, EquipmentSlotGroup.bySlot(armor.getEquipmentSlot()));
        if (item instanceof ShieldItem) return new Target(Rune.Kind.ARMOR, EquipmentSlotGroup.OFFHAND);
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem || item instanceof MaceItem) {
            return new Target(Rune.Kind.WEAPON, EquipmentSlotGroup.MAINHAND);
        }
        if (item instanceof DiggerItem) return new Target(Rune.Kind.TOOL, EquipmentSlotGroup.MAINHAND);
        // armas de outros módulos (lâminas, foices...) costumam ser TieredItem com dano
        if (item instanceof TieredItem) return new Target(Rune.Kind.WEAPON, EquipmentSlotGroup.MAINHAND);
        return null;
    }

    public static boolean isMithril(ItemStack stack) {
        if (stack.getItem() instanceof TieredItem t && t.getTier() == DwarfRegistry.MITHRIL_TIER) return true;
        if (stack.getItem() instanceof ArmorItem a && a.getMaterial().is(DwarfRegistry.MITHRIL_MATERIAL.getKey())) return true;
        return stack.is(DwarfRegistry.HERO_SHIELD.get());
    }

    public static double amount(Rune rune, Rune.Kind kind, ItemStack stack) {
        double a = rune.bonus(kind).amount();
        return isMithril(stack) ? a * Rune.MITHRIL_MULT : a;
    }

    /** Soma o bônus da runa aos modificadores do item (vale só no espaço certo, como os do vanilla). */
    public static void onAttributes(ItemAttributeModifierEvent e) {
        ItemStack stack = e.getItemStack();
        Rune rune = of(stack);
        if (rune == null) return;
        Target t = target(stack);
        if (t == null) return;
        Rune.Bonus b = rune.bonus(t.kind());
        // id por espaço: duas peças de armadura com runa não se sobrescrevem
        e.addModifier(b.attribute(), new AttributeModifier(IsekaiMahou.id("dwarf_rune_" + t.slot().getSerializedName()),
                amount(rune, t.kind(), stack), b.operation()), t.slot());
    }

    private Runes() {}
}
