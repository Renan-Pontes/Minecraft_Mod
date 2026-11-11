package com.renanpontes.isekaimahou.feature.class_ranger.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Rarity;

/**
 * Os 4 graus do Traje Selvagem do Ranger (v1.0). O I vem no kit; II/III/IV chegam nos Círculos 3/5/8.
 * Números já com a inflação da v1.0 (defesa ×1,5 das roupas de Destreza, Foco ×3):
 * <ul>
 *   <li>defesa total 14 / 19 / 24 / 28, resistência por peça 0 / 1 / 1,5 / 3;</li>
 *   <li>Foco máximo (conjunto) +30 / +75 / +135 / +225, velocidade +4 / +6 / +9 / +12%,
 *   regeneração do Foco +0 / +8 / +15 / +25% e queda segura até +3 blocos no IV.</li>
 * </ul>
 * Ids: {@code ranger_<grau>_<peça>} (scout, tracker, warden, beastlord) — não colidem com os trajes antigos do outfits.
 */
public enum RangerOutfitTier {
    //        id           n  círc defesa {cab, peito, pernas, pés} tough  durab raridade         cor
    SCOUT("scout", 1, 1, new int[] {2, 6, 4, 2}, 0.0F, 15, Rarity.COMMON, 0x9AC46A,
            new double[] {10, 10, 5, 5}, new double[] {0, 0, 0.02, 0.02}, new double[] {0, 0, 0, 0}, 0),
    TRACKER("tracker", 2, 3, new int[] {3, 7, 6, 3}, 1.0F, 22, Rarity.UNCOMMON, 0x6FD8A0,
            new double[] {15, 30, 15, 15}, new double[] {0, 0, 0.03, 0.03}, new double[] {0.04, 0, 0, 0.04}, 0),
    WARDEN("warden", 3, 5, new int[] {3, 9, 8, 4}, 1.5F, 30, Rarity.RARE, 0x7AE8D0,
            new double[] {30, 45, 30, 30}, new double[] {0, 0.03, 0.03, 0.03}, new double[] {0.05, 0.05, 0, 0.05}, 1),
    BEASTLORD("beastlord", 4, 8, new int[] {4, 11, 9, 4}, 3.0F, 40, Rarity.EPIC, 0xFFD24A,
            new double[] {45, 90, 45, 45}, new double[] {0, 0.04, 0.04, 0.04}, new double[] {0.10, 0.05, 0, 0.10}, 3);

    /** Graus entregues ao subir de Círculo (o I vem no kit inicial). */
    public static final RangerOutfitTier[] REWARDS = {TRACKER, WARDEN, BEASTLORD};
    /** Ordem das peças (cabeça → pés). */
    public static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
            ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

    public final String id;
    public final int number;
    public final int circle;
    private final int[] defense;
    public final float toughness;
    public final int durabilityFactor;
    public final Rarity rarity;
    public final int color;
    private final double[] focus;
    private final double[] speed;
    private final double[] regen;
    /** Blocos de queda segura nas botas. */
    public final int safeFall;

    RangerOutfitTier(String id, int number, int circle, int[] defense, float toughness, int durabilityFactor, Rarity rarity,
            int color, double[] focus, double[] speed, double[] regen, int safeFall) {
        this.id = id;
        this.number = number;
        this.circle = circle;
        this.defense = defense;
        this.toughness = toughness;
        this.durabilityFactor = durabilityFactor;
        this.rarity = rarity;
        this.color = color;
        this.focus = focus;
        this.speed = speed;
        this.regen = regen;
        this.safeFall = safeFall;
    }

    public static int index(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 0;
            case CHESTPLATE, BODY -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    public int defense(ArmorItem.Type type) {
        return defense[index(type)];
    }

    public int totalDefense() {
        int s = 0;
        for (int d : defense) s += d;
        return s;
    }

    public double focus(ArmorItem.Type type) {
        return focus[index(type)];
    }

    public double speed(ArmorItem.Type type) {
        return speed[index(type)];
    }

    public double regen(ArmorItem.Type type) {
        return regen[index(type)];
    }

    public double totalFocus() {
        double s = 0;
        for (double d : focus) s += d;
        return s;
    }

    public double totalSpeed() {
        double s = 0;
        for (double d : speed) s += d;
        return s;
    }

    /** Id do item: {@code ranger_<grau>_<peça>}. */
    public String itemId(ArmorItem.Type type) {
        return "ranger_" + id + "_" + type.getName();
    }

    /** Id do material e do modelo 3D ({@code geo/item/armor/ranger_garb_<grau>.geo.json}). */
    public String modelId() {
        return "ranger_garb_" + id;
    }

    /** Flag gravada quando o traje deste grau já foi entregue. */
    public String flag() {
        return "ranger.garb." + id;
    }

    public MutableComponent displayName() {
        return Component.translatable(langKey("outfit"));
    }

    /** Chave de tradução do grau: {@code <grupo>.isekai_mahou.ranger_garb.<grau>} (nome: outfit; frase: tooltip). */
    public String langKey(String group) {
        return group + ".isekai_mahou.ranger_garb." + id;
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
            case SCOUT -> ChatFormatting.WHITE;
            case TRACKER -> ChatFormatting.YELLOW;
            case WARDEN -> ChatFormatting.AQUA;
            case BEASTLORD -> ChatFormatting.LIGHT_PURPLE;
        };
    }

}
