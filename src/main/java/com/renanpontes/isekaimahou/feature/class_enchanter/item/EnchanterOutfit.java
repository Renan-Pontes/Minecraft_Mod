package com.renanpontes.isekaimahou.feature.class_enchanter.item;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Graus do traje do Encantador (v1.0): I Noviço (kit), II Inscritor (Círculo 3), III Mestre Encantador (Círculo 5) e
 * IV Soberano dos Encantos (Círculo 8). Armadura de lutador (defesa ×1,5 da v0.9, como pede a inflação de números),
 * Vigor, vida e um pouco de velocidade; o traje COMPLETO faz os encantamentos durarem mais ({@link #setDurationBonus}).
 */
public enum EnchanterOutfit {
    //        id          num círc  defesa {cab,peito,pernas,pés}  tough  repulsão durab  raridade         cor
    NOVICE("novice", 1, 1, new int[] {3, 7, 6, 2}, 0.5F, 0.0F, 15, Rarity.COMMON, 0x5FE3C0),
    ADEPT("adept", 2, 3, new int[] {3, 9, 7, 3}, 1.5F, 0.0F, 24, Rarity.UNCOMMON, 0x8AF0FF),
    MASTER("master", 3, 5, new int[] {4, 10, 9, 4}, 2.5F, 0.05F, 33, Rarity.RARE, 0xFFD27A),
    SOVEREIGN("sovereign", 4, 8, new int[] {4, 12, 10, 4}, 3.5F, 0.1F, 40, Rarity.EPIC, 0xE8FFFF);

    /** Graus entregues ao subir de Círculo (o I vem no kit). */
    public static final EnchanterOutfit[] REWARDS = {ADEPT, MASTER, SOVEREIGN};

    public final String id;
    public final int number;
    public final int circle;
    private final int[] defense;
    public final float toughness;
    public final float knockback;
    public final int durabilityFactor;
    public final Rarity rarity;
    public final int color;

    EnchanterOutfit(String id, int number, int circle, int[] defense, float toughness, float knockback, int durabilityFactor,
                    Rarity rarity, int color) {
        this.id = id;
        this.number = number;
        this.circle = circle;
        this.defense = defense;
        this.toughness = toughness;
        this.knockback = knockback;
        this.durabilityFactor = durabilityFactor;
        this.rarity = rarity;
        this.color = color;
    }

    /** {@code enchanter_<grau>_<peça>} (ids prefixados pela classe). */
    public String itemId(ArmorItem.Type type) {
        return "enchanter_" + id + "_" + type.getName();
    }

    /** Material e textura vanilla de reserva ({@code textures/models/armor/<id>_layer_1.png}). */
    public String materialId() {
        return "enchanter_" + id;
    }

    /** Modelo GeckoLib: {@code geo/item/armor/enchanter_<grau>.geo.json} + {@code textures/item/armor/enchanter_<grau>.png}. */
    public ResourceLocation modelId() {
        return IsekaiMahou.id("armor/enchanter_" + id);
    }

    /** Flag gravada quando o grau já foi entregue. */
    public String flag() {
        return "enchanter.outfit." + id;
    }

    public int defense(ArmorItem.Type type) {
        return defense[index(type)];
    }

    public int totalDefense() {
        int s = 0;
        for (int d : defense) s += d;
        return s;
    }

    static int index(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 0;
            case CHESTPLATE, BODY -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    public ArmorMaterial buildMaterial(Holder<SoundEvent> equip, Supplier<Ingredient> repair) {
        Map<ArmorItem.Type, Integer> def = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type t : ArmorItem.Type.values()) def.put(t, defense(t));
        return new ArmorMaterial(def, 18, equip, repair, List.of(new ArmorMaterial.Layer(IsekaiMahou.id(materialId()))),
                toughness, knockback);
    }

    /** Bônus extra por peça (além da armadura do material). */
    public record Bonus(Holder<Attribute> attribute, String key, double amount, AttributeModifier.Operation op, boolean percent) {}

    // Tabelas [grau][peça] — vigor (×3 da inflação de recurso), vida, velocidade, poder
    private static final double[][] VIGOR = {{0, 15, 10, 5}, {10, 25, 15, 10}, {15, 45, 30, 15}, {30, 70, 50, 30}};
    private static final double[][] HEALTH = {{0, 2, 0, 0}, {0, 4, 2, 0}, {2, 4, 4, 0}, {2, 6, 4, 2}};
    private static final double[][] SPEED = {{0, 0, 0, 0}, {0, 0, 0, 0.03}, {0, 0, 0.03, 0.03}, {0, 0, 0.04, 0.04}};
    private static final double[][] POWER = {{0, 0, 0, 0}, {0, 0, 0, 0}, {0.03, 0, 0, 0}, {0.04, 0.04, 0, 0}};

    public List<Bonus> bonuses(ArmorItem.Type type) {
        int t = ordinal(), p = index(type);
        List<Bonus> out = new ArrayList<>();
        if (VIGOR[t][p] != 0) out.add(new Bonus(CoreRegistries.MAX_MANA, "vigor", VIGOR[t][p], AttributeModifier.Operation.ADD_VALUE, false));
        if (HEALTH[t][p] != 0) out.add(new Bonus(Attributes.MAX_HEALTH, "health", HEALTH[t][p], AttributeModifier.Operation.ADD_VALUE, false));
        if (SPEED[t][p] != 0) out.add(new Bonus(Attributes.MOVEMENT_SPEED, "speed", SPEED[t][p],
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE, true));
        if (POWER[t][p] != 0) out.add(new Bonus(CoreRegistries.SPELL_POWER, "power", POWER[t][p],
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE, true));
        return out;
    }

    /** Conjunto completo deste grau: encantamentos do Encantador duram +10/15/20/25%. */
    public float setDurationBonus() {
        return 0.05F + 0.05F * number;
    }

    public MutableComponent displayName() {
        return Component.translatable(nameKey());
    }

    public String nameKey() {
        return String.join(".", "outfit.isekai_mahou.enchanter.tier", id);
    }

    /** Frase de ambientação da peça (uma por grau). */
    public String flavorKey() {
        return String.join(".", "tooltip.isekai_mahou.enchanter_outfit.flavor", id);
    }

    public String roman() {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "IV";
        };
    }

    public ChatFormatting chatColor() {
        return switch (this) {
            case NOVICE -> ChatFormatting.WHITE;
            case ADEPT -> ChatFormatting.YELLOW;
            case MASTER -> ChatFormatting.AQUA;
            case SOVEREIGN -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    @Nullable
    public static EnchanterOutfit byId(String id) {
        for (EnchanterOutfit t : values()) if (t.id.equals(id)) return t;
        return null;
    }
}
