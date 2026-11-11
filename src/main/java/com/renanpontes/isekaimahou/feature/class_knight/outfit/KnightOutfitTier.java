package com.renanpontes.isekaimahou.feature.class_knight.outfit;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Rarity;

/**
 * Os 4 graus da armadura do Cavaleiro Rúnico (modelo 3D GeckoLib gerado por tools/gen/class_knight.py):
 * I Escudeiro Rúnico (kit) · II Guarda das Runas (Círculo 3) · III Cavaleiro da Távola (Círculo 5) · IV Rei Rúnico
 * (Círculo 8). Números já com a inflação da v1.0 (defesa ×1,5, recurso ×3, vida ×2) sobre a tabela das Físicas.
 * <p>{@code sharedFlag} é a mesma flag que o módulo de trajes usa ({@code gear.armor.<grau>}): gravá-la aqui impede
 * que um traje antigo da classe seja entregue em dobro no mesmo Círculo.
 */
public enum KnightOutfitTier {
    //         id          nº círc  defesa {cab, peito, pernas, pés} tough repuls durab raridade         cor       flag do traje
    SQUIRE("squire", 1, 1, new int[] {3, 7, 6, 2}, 0.5F, 0.0F, 18, Rarity.COMMON, 0xB8C4D8, null,
            new double[][] {{0, 18, 12, 0}, {0, 4, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}),
    RUNEGUARD("runeguard", 2, 3, new int[] {3, 8, 7, 3}, 1.5F, 0.0F, 26, Rarity.UNCOMMON, 0x7FA8FF, "gear.armor.adept",
            new double[][] {{12, 24, 18, 6}, {0, 4, 4, 0}, {0, 0.5, 0, 0}, {0, 0, 0, 0.05}}),
    TEMPLAR("templar", 3, 5, new int[] {4, 10, 8, 4}, 2.5F, 0.05F, 34, Rarity.RARE, 0xFFD86B, "gear.armor.master",
            new double[][] {{18, 42, 30, 15}, {0, 8, 4, 0}, {0, 0.5, 0.5, 0}, {0, 0, 0.05, 0.05}}),
    RUNEKING("runeking", 4, 8, new int[] {5, 11, 9, 5}, 4.0F, 0.1F, 42, Rarity.EPIC, 0xE8F4FF, "gear.armor.archmage",
            new double[][] {{30, 72, 48, 30}, {4, 8, 8, 0}, {0.5, 1.0, 0.5, 0}, {0, 0, 0.05, 0.10}});

    /** Graus entregues ao subir de Círculo (o I vem no kit). */
    public static final KnightOutfitTier[] REWARDS = {RUNEGUARD, TEMPLAR, RUNEKING};

    public final String id;
    public final int number;
    public final int circle;
    private final int[] defense;
    public final float toughness;
    public final float knockback;
    public final int durability;
    public final Rarity rarity;
    public final int color;
    @Nullable
    public final String sharedFlag;
    /** [estatística][peça]: recurso máximo, vida, dano corpo a corpo, regeneração do recurso (fração). */
    private final double[][] bonus;

    KnightOutfitTier(String id, int number, int circle, int[] defense, float toughness, float knockback, int durability,
            Rarity rarity, int color, @Nullable String sharedFlag, double[][] bonus) {
        this.id = id;
        this.number = number;
        this.circle = circle;
        this.defense = defense;
        this.toughness = toughness;
        this.knockback = knockback;
        this.durability = durability;
        this.rarity = rarity;
        this.color = color;
        this.sharedFlag = sharedFlag;
        this.bonus = bonus;
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

    public double resource(ArmorItem.Type t) { return bonus[0][piece(t)]; }
    public double health(ArmorItem.Type t) { return bonus[1][piece(t)]; }
    public double melee(ArmorItem.Type t) { return bonus[2][piece(t)]; }
    public double regen(ArmorItem.Type t) { return bonus[3][piece(t)]; }

    public double total(int stat) {
        double s = 0;
        for (double v : bonus[stat]) s += v;
        return s;
    }

    /** Id do item: {@code knight_<grau>_<peça>}. */
    public String itemId(ArmorItem.Type type) {
        return "knight_" + id + "_" + type.getName();
    }

    /** Id do material/modelo: {@code knight_<grau>}. */
    public String modelName() {
        return "knight_" + id;
    }

    /** Flag própria do módulo: grau já entregue. */
    public String flag() {
        return "knight.gear." + id;
    }

    /** O grau I não brilha; II–IV têm glowmask (runas e gemas). */
    public boolean glows() {
        return this != SQUIRE;
    }

    @Nullable
    public KnightOutfitTier next() {
        int i = ordinal() + 1;
        return i < values().length ? values()[i] : null;
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
        return com.renanpontes.isekaimahou.feature.class_knight.KnightText.tr("outfit", id);
    }

    public ChatFormatting chatColor() {
        return switch (this) {
            case SQUIRE -> ChatFormatting.WHITE;
            case RUNEGUARD -> ChatFormatting.YELLOW;
            case TEMPLAR -> ChatFormatting.AQUA;
            case RUNEKING -> ChatFormatting.LIGHT_PURPLE;
        };
    }
}
