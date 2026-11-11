package com.renanpontes.isekaimahou.feature.class_gunslinger.item;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Rarity;

/**
 * Graus do traje do Pistoleiro (padrão do módulo outfits: I no kit, II no Círculo 3, III no 5, IV no 8). Os números
 * seguem o perfil "Destreza" do outfits com a inflação da v1.0 (§0.1): defesa ×1,5 e resistência ×1,5; o Foco extra é
 * ×{@code ResourceTraining.MANA_SCALE} (a barra ficou 3× maior).
 * <p>Conjunto completo: defesa 15 / 20 / 25 / 30, +30 / +75 / +135 / +225 Foco, até +12% de velocidade.
 */
public enum GsOutfitTier {
    //           id          nº círc  defesa {cab,peito,pernas,pés} tough durab raridade        cor
    APPRENTICE("apprentice", 1, 1, new int[] {2, 6, 5, 2}, 0.0F, 13, Rarity.COMMON, 0xD9A441,
            new double[][] {{15, 15, 0, 0}, {0, 0, 0.02, 0.02}, {0, 0, 0, 0}, {0, 0, 0, 0}}),
    ADEPT("adept", 2, 3, new int[] {3, 8, 6, 3}, 0.75F, 20, Rarity.UNCOMMON, 0xB8C4D6,
            new double[][] {{15, 30, 15, 15}, {0, 0, 0.03, 0.03}, {0.04, 0, 0, 0.04}, {0, 0, 0, 0}}),
    MASTER("master", 3, 5, new int[] {3, 9, 8, 5}, 1.5F, 28, Rarity.RARE, 0xE0503A,
            new double[][] {{30, 45, 30, 30}, {0, 0.03, 0.03, 0.03}, {0.05, 0.05, 0, 0.05}, {0.04, 0, 0, 0}}),
    ARCHMAGE("archmage", 4, 8, new int[] {5, 11, 9, 5}, 3.0F, 37, Rarity.EPIC, 0xFFB22A,
            new double[][] {{45, 90, 45, 45}, {0, 0.04, 0.04, 0.04}, {0.10, 0.05, 0, 0.10}, {0.04, 0.04, 0, 0}});

    /** Graus entregues ao subir de Círculo (o I vem no kit). */
    public static final GsOutfitTier[] REWARDS = {ADEPT, MASTER, ARCHMAGE};
    /** Ordem das peças (cabeça → pés). */
    public static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
            ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

    /** Prefixo do nome do grau ({@code outfit.isekai_mahou.gunslinger.tier.<id>}). */
    private static final String NAME_KEY = "outfit.isekai_mahou.gunslinger.tier.";

    public final String id;
    public final int number;
    public final int circle;
    private final int[] defense;
    /** Resistência (toughness) por peça. */
    public final float toughness;
    public final int durabilityFactor;
    public final Rarity rarity;
    public final int color;
    /** [stat][peça]: Foco máximo, velocidade, regeneração, poder. */
    private final double[][] bonuses;

    GsOutfitTier(String id, int number, int circle, int[] defense, float toughness, int durabilityFactor, Rarity rarity,
                 int color, double[][] bonuses) {
        this.id = id;
        this.number = number;
        this.circle = circle;
        this.defense = defense;
        this.toughness = toughness;
        this.durabilityFactor = durabilityFactor;
        this.rarity = rarity;
        this.color = color;
        this.bonuses = bonuses;
    }

    public static int piece(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 0;
            case CHESTPLATE, BODY -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    public int defense(ArmorItem.Type type) {
        return defense[piece(type)];
    }

    public int totalDefense() {
        int s = 0;
        for (int d : defense) s += d;
        return s;
    }

    /** Bônus de uma peça: 0 Foco, 1 velocidade, 2 regeneração, 3 poder. */
    public double bonus(int stat, ArmorItem.Type type) {
        return bonuses[stat][piece(type)];
    }

    public double total(int stat) {
        double s = 0;
        for (double v : bonuses[stat]) s += v;
        return s;
    }

    /** Id do item: grau I {@code gunslinger_<peça>}, os outros {@code gunslinger_<grau>_<peça>}. */
    public String itemId(ArmorItem.Type type) {
        return this == APPRENTICE ? "gunslinger_" + type.getName() : "gunslinger_" + id + "_" + type.getName();
    }

    /** Id do material e do modelo GeckoLib ({@code geo/item/armor/<id>.geo.json}). */
    public String materialId() {
        return this == APPRENTICE ? "outfit_gunslinger" : "outfit_gunslinger_" + id;
    }

    public String flag() {
        return "gunslinger.outfit." + id;
    }

    public MutableComponent displayName() {
        return Component.translatable(NAME_KEY + id);
    }

    public String flavorKey() {
        return "tooltip.isekai_mahou.gunslinger.outfit." + id;
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
            case APPRENTICE -> ChatFormatting.WHITE;
            case ADEPT -> ChatFormatting.YELLOW;
            case MASTER -> ChatFormatting.AQUA;
            case ARCHMAGE -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    /** Graus II–IV têm runas/gemas que brilham (glowmask). */
    public boolean glows() {
        return this != APPRENTICE;
    }

    @Nullable
    public GsOutfitTier next() {
        int i = ordinal() + 1;
        return i < values().length ? values()[i] : null;
    }

    @Nullable
    public static GsOutfitTier byId(String id) {
        for (GsOutfitTier t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }
}
