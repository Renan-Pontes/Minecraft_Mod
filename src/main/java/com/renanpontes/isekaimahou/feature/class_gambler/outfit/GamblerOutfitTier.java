package com.renanpontes.isekaimahou.feature.class_gambler.outfit;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Rarity;

/**
 * Graus do traje do Apostador (padrão dos trajes de classe: I no kit, II/III/IV entregues nos Círculos 3/5/8).
 * Números da categoria Destreza com a inflação da v1.0 (defesa ×1,5, Foco ×3):
 * <pre>
 *  grau            defesa (cab/peito/pernas/pés)  total  resist.  Foco  veloc.  regen  poder  Sorte(vanilla)
 *  I   Crupiê      2/6/5/2                         15     0       +30   +6%    —      —      —
 *  II  Mesa Alta   3/8/6/3                         20     0,75    +75   +6%    +8%    —      +1
 *  III Rei do Cass.3/9/8/5                         25     1,5     +135  +9%    +15%   +4%    +2
 *  IV  Senhor Fort.5/11/9/5                        30     3       +225  +12%   +25%   +8%    +3
 * </pre>
 * Traje completo (qualquer grau): +1 de Sorte nas rolagens e +3% de esquiva da sorte.
 */
public enum GamblerOutfitTier {
    DEALER("dealer", 1, 1, new int[] {2, 6, 5, 2}, 0F, new double[] {15, 15, 0, 0}, new double[] {0, 0, 0.03, 0.03},
            new double[] {0, 0, 0, 0}, new double[] {0, 0, 0, 0}, 0, 13, Rarity.COMMON, 0xC9B48A),
    HIGH_ROLLER("high_roller", 2, 3, new int[] {3, 8, 6, 3}, 0.75F, new double[] {15, 30, 15, 15}, new double[] {0, 0, 0.03, 0.03},
            new double[] {0.04, 0, 0, 0.04}, new double[] {0, 0, 0, 0}, 0.25, 20, Rarity.UNCOMMON, 0x7FC8FF),
    CASINO_KING("casino_king", 3, 5, new int[] {3, 9, 8, 5}, 1.5F, new double[] {30, 45, 30, 30}, new double[] {0, 0.03, 0.03, 0.03},
            new double[] {0.05, 0.05, 0, 0.05}, new double[] {0.04, 0, 0, 0}, 0.5, 28, Rarity.RARE, 0xFFC94A),
    FORTUNE_LORD("fortune_lord", 4, 8, new int[] {5, 11, 9, 5}, 3.0F, new double[] {45, 90, 45, 45}, new double[] {0, 0.04, 0.04, 0.04},
            new double[] {0.10, 0.05, 0, 0.10}, new double[] {0.04, 0.04, 0, 0}, 0.75, 37, Rarity.EPIC, 0xFF4AD2);

    /** Graus entregues ao subir de Círculo (o I vem no kit inicial). */
    public static final GamblerOutfitTier[] REWARDS = {HIGH_ROLLER, CASINO_KING, FORTUNE_LORD};

    public final String id;
    public final int number;
    public final int circle;
    private final int[] defense;
    public final float toughness;
    private final double[] focus;
    private final double[] speed;
    private final double[] regen;
    private final double[] power;
    /** Sorte vanilla (saques) por peça. */
    public final double luck;
    public final int durabilityFactor;
    public final Rarity rarity;
    public final int color;

    GamblerOutfitTier(String id, int number, int circle, int[] defense, float toughness, double[] focus, double[] speed,
            double[] regen, double[] power, double luck, int durabilityFactor, Rarity rarity, int color) {
        this.id = id;
        this.number = number;
        this.circle = circle;
        this.defense = defense;
        this.toughness = toughness;
        this.focus = focus;
        this.speed = speed;
        this.regen = regen;
        this.power = power;
        this.luck = luck;
        this.durabilityFactor = durabilityFactor;
        this.rarity = rarity;
        this.color = color;
    }

    static int piece(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 0;
            case CHESTPLATE, BODY -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    public int defense(ArmorItem.Type t) {
        return defense[piece(t)];
    }

    public double focus(ArmorItem.Type t) {
        return focus[piece(t)];
    }

    public double speed(ArmorItem.Type t) {
        return speed[piece(t)];
    }

    public double regen(ArmorItem.Type t) {
        return regen[piece(t)];
    }

    public double power(ArmorItem.Type t) {
        return power[piece(t)];
    }

    public int totalDefense() {
        int s = 0;
        for (int d : defense) s += d;
        return s;
    }

    public double totalFocus() {
        double s = 0;
        for (double d : focus) s += d;
        return s;
    }

    /** Grau I mantém o id curto ({@code gambler_<peça>}); os outros {@code gambler_<grau>_<peça>}. */
    public String itemId(ArmorItem.Type type) {
        return this == DEALER ? "gambler_" + type.getName() : "gambler_" + id + "_" + type.getName();
    }

    /** Material e modelo ({@code outfit_gambler[_<grau>]}). */
    public String materialId() {
        return this == DEALER ? "outfit_gambler" : "outfit_gambler_" + id;
    }

    /** Flag gravada quando o grau já foi entregue. */
    public String flag() {
        return "gambler_outfit." + id;
    }

    public String roman() {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "IV";
        };
    }

    public MutableComponent displayName() {
        return Component.translatable(String.join("", "outfit.isekai_mahou.gambler.", id));
    }

    public ChatFormatting chatColor() {
        return switch (this) {
            case DEALER -> ChatFormatting.WHITE;
            case HIGH_ROLLER -> ChatFormatting.YELLOW;
            case CASINO_KING -> ChatFormatting.AQUA;
            case FORTUNE_LORD -> ChatFormatting.LIGHT_PURPLE;
        };
    }
}
