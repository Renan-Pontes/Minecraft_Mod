package com.renanpontes.isekaimahou.feature.class_smith.item;

import com.renanpontes.isekaimahou.feature.class_smith.SmithKeys;
import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Rarity;

/**
 * Graus da roupa do Ferreiro (mesmo ritmo das outras classes: I no kit, II no Círculo 3, III no 5, IV no 8).
 * Números da categoria Física com a inflação da v1.0 (defesa ×1,5, vida ×2, recurso ×3). Além disso cada peça protege
 * do fogo da forja ({@link #fireWard}): o conjunto IV completo corta 60% do dano de fogo/lava.
 */
public enum SmithOutfitTier {
    //           id          num círc  defesa {cab, peito, pernas, pés}  tough  repuls. durab raridade         cor       fogo
    APPRENTICE("apprentice", 1, 1, new int[] {3, 8, 6, 2}, 0.75F, 0.0F, 15, Rarity.COMMON, 0xC08A52, 0.05F),
    ADEPT("adept", 2, 3, new int[] {3, 9, 8, 3}, 1.5F, 0.0F, 24, Rarity.UNCOMMON, 0xFF9A4A, 0.08F),
    MASTER("master", 3, 5, new int[] {5, 11, 9, 5}, 3.0F, 0.05F, 33, Rarity.RARE, 0xFFC94A, 0.12F),
    ARCHMAGE("archmage", 4, 8, new int[] {5, 14, 11, 5}, 5.0F, 0.1F, 40, Rarity.EPIC, 0x8FA8FF, 0.15F);

    public static final SmithOutfitTier[] VALUES = values();
    /** Ordem das peças (cabeça → pés). */
    public static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
            ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

    public final String id;
    public final int number;
    public final int circle;
    private final int[] defense;
    public final float toughness;
    public final float knockbackResistance;
    public final int durabilityFactor;
    public final Rarity rarity;
    public final int color;
    /** Fração do dano de fogo que CADA peça vestida corta. */
    public final float fireWard;

    SmithOutfitTier(String id, int number, int circle, int[] defense, float toughness, float knockbackResistance,
                    int durabilityFactor, Rarity rarity, int color, float fireWard) {
        this.id = id;
        this.number = number;
        this.circle = circle;
        this.defense = defense;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.durabilityFactor = durabilityFactor;
        this.rarity = rarity;
        this.color = color;
        this.fireWard = fireWard;
    }

    public int defense(ArmorItem.Type type) {
        return defense[index(type)];
    }

    /** Id do item: grau I = {@code smith_<peça>}; os outros {@code smith_<grau>_<peça>}. */
    public String itemId(ArmorItem.Type type) {
        return this == APPRENTICE ? "smith_" + type.getName() : "smith_" + id + "_" + type.getName();
    }

    /** Id do material e do modelo 3D ({@code outfit_smith[_grau]}). */
    public String modelName() {
        return this == APPRENTICE ? "outfit_smith" : "outfit_smith_" + id;
    }

    public String materialId() {
        return "smith_outfit_" + id;
    }

    public String flag() {
        return "smith.outfit." + id;
    }

    public MutableComponent displayName() {
        return Component.translatable(SmithKeys.OUTFIT_TIER + id);
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
            case APPRENTICE -> ChatFormatting.GOLD;
            case ADEPT -> ChatFormatting.YELLOW;
            case MASTER -> ChatFormatting.AQUA;
            case ARCHMAGE -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    /** Grau seguinte (null no IV). */
    public SmithOutfitTier next() {
        return ordinal() + 1 < VALUES.length ? VALUES[ordinal() + 1] : null;
    }

    public static int index(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 0;
            case CHESTPLATE, BODY -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    /** Um bônus de atributo da peça. */
    public record Bonus(String id, Holder<Attribute> attribute, double amount, AttributeModifier.Operation op) {}

    private static final double[][] RESOURCE = {{0, 18, 12, 0}, {12, 24, 18, 6}, {18, 42, 30, 15}, {30, 72, 48, 30}};
    private static final double[][] HEALTH = {{0, 4, 0, 0}, {0, 4, 4, 0}, {0, 8, 4, 0}, {4, 8, 8, 0}};
    private static final double[][] MELEE = {{0, 0, 0, 0}, {0, 0.5, 0, 0}, {0, 0.5, 0.5, 0}, {0.5, 1.0, 0.5, 0}};
    private static final double[][] REGEN = {{0, 0, 0, 0}, {0, 0, 0, 0.05}, {0, 0, 0.05, 0.05}, {0, 0, 0.05, 0.10}};

    /** Bônus da peça (só os que não são zero). */
    public java.util.List<Bonus> bonuses(ArmorItem.Type type) {
        int t = ordinal(), p = index(type);
        java.util.List<Bonus> out = new java.util.ArrayList<>();
        if (RESOURCE[t][p] > 0) out.add(new Bonus("resource", CoreRegistries.MAX_MANA, RESOURCE[t][p], AttributeModifier.Operation.ADD_VALUE));
        if (HEALTH[t][p] > 0) out.add(new Bonus("health", Attributes.MAX_HEALTH, HEALTH[t][p], AttributeModifier.Operation.ADD_VALUE));
        if (MELEE[t][p] > 0) out.add(new Bonus("melee", Attributes.ATTACK_DAMAGE, MELEE[t][p], AttributeModifier.Operation.ADD_VALUE));
        if (REGEN[t][p] > 0) out.add(new Bonus("regen", CoreRegistries.MANA_REGEN, REGEN[t][p], AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        return out;
    }
}
