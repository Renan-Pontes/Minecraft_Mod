package com.renanpontes.isekaimahou.feature.dwarf_hold.rune;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * Runas da Forja Rúnica: uma por essência. Cada uma dá um bônus PEQUENO e FIXO, diferente para arma, armadura e
 * ferramenta. No equipamento de Mithril o bônus é ×1,5 ("o Mithril canta com as runas").
 */
public enum Rune {
    EMBER("ember", Element.FIRE, 0xFF7A3A,
            new Bonus(Attributes.ATTACK_DAMAGE, 1.5, Operation.ADD_VALUE),
            new Bonus(Attributes.ARMOR, 1.0, Operation.ADD_VALUE),
            new Bonus(Attributes.ATTACK_DAMAGE, 1.0, Operation.ADD_VALUE)),
    TIDE("tide", Element.WATER, 0x4AA8FF,
            new Bonus(Attributes.ENTITY_INTERACTION_RANGE, 0.5, Operation.ADD_VALUE),
            new Bonus(Attributes.MAX_HEALTH, 2.0, Operation.ADD_VALUE),
            new Bonus(Attributes.BLOCK_INTERACTION_RANGE, 1.0, Operation.ADD_VALUE)),
    MOUNTAIN("mountain", Element.EARTH, 0xC08A4A,
            new Bonus(Attributes.ATTACK_KNOCKBACK, 0.5, Operation.ADD_VALUE),
            new Bonus(Attributes.ARMOR_TOUGHNESS, 1.0, Operation.ADD_VALUE),
            new Bonus(Attributes.MINING_EFFICIENCY, 4.0, Operation.ADD_VALUE)),
    GALE("gale", Element.WIND, 0x9AF0C8,
            new Bonus(Attributes.ATTACK_SPEED, 0.2, Operation.ADD_VALUE),
            new Bonus(Attributes.MOVEMENT_SPEED, 0.03, Operation.ADD_MULTIPLIED_BASE),
            new Bonus(Attributes.MINING_EFFICIENCY, 2.0, Operation.ADD_VALUE)),
    DAWN("dawn", Element.LIGHT, 0xFFE27A,
            new Bonus(Attributes.LUCK, 1.0, Operation.ADD_VALUE),
            new Bonus(Attributes.LUCK, 0.5, Operation.ADD_VALUE),
            new Bonus(Attributes.LUCK, 1.0, Operation.ADD_VALUE)),
    SHADE("shade", Element.SHADOW, 0x9A6AE0,
            new Bonus(Attributes.SWEEPING_DAMAGE_RATIO, 0.25, Operation.ADD_VALUE),
            new Bonus(Attributes.SNEAKING_SPEED, 0.1, Operation.ADD_VALUE),
            new Bonus(Attributes.SUBMERGED_MINING_SPEED, 0.5, Operation.ADD_VALUE)),
    ARCANE("arcane", Element.ARCANE, 0xC08CFF,
            new Bonus(CoreRegistries.SPELL_POWER, 0.04, Operation.ADD_VALUE),
            new Bonus(CoreRegistries.MAX_MANA, 10.0, Operation.ADD_VALUE),
            new Bonus(CoreRegistries.MANA_REGEN, 0.05, Operation.ADD_VALUE));

    /** Um bônus de atributo. */
    public record Bonus(Holder<Attribute> attribute, double amount, Operation operation) {}

    /** Que tipo de peça recebe a runa (cada tipo usa o seu bônus). */
    public enum Kind { WEAPON, ARMOR, TOOL }

    public static final float MITHRIL_MULT = 1.5F;

    public final String id;
    public final Element element;
    public final int color;
    private final Bonus weapon;
    private final Bonus armor;
    private final Bonus tool;

    Rune(String id, Element element, int color, Bonus weapon, Bonus armor, Bonus tool) {
        this.id = id;
        this.element = element;
        this.color = color;
        this.weapon = weapon;
        this.armor = armor;
        this.tool = tool;
    }

    public Bonus bonus(Kind kind) {
        return switch (kind) {
            case WEAPON -> weapon;
            case ARMOR -> armor;
            case TOOL -> tool;
        };
    }

    public MutableComponent displayName() {
        return Component.translatable("rune.isekai_mahou.dwarf_hold." + id);
    }

    public MutableComponent effectText(Kind kind) {
        return Component.translatable("rune.isekai_mahou.dwarf_hold." + id + "." + kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Nullable
    public static Rune byId(@Nullable String id) {
        if (id == null) return null;
        for (Rune r : values()) if (r.id.equals(id)) return r;
        return null;
    }

    @Nullable
    public static Rune byEssence(ItemStack stack) {
        for (Rune r : values()) {
            var holder = CoreRegistries.ESSENCES.get(r.element);
            if (holder != null && stack.is(holder.get())) return r;
        }
        return null;
    }
}
