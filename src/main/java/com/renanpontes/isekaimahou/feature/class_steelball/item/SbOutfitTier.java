package com.renanpontes.isekaimahou.feature.class_steelball.item;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Rarity;

/**
 * Os 4 graus da roupa do Steel Ball (mesmo esquema das roupas de classe: I no kit, II no Círculo 3, III no 5, IV no 8).
 * Números já com a inflação da v1.0 (defesa ×1,5, Foco ×3). Roupa leve de cavaleiro: pouca defesa, muito Foco,
 * velocidade, regeneração e queda segura.
 * <ul>
 *   <li>I Jóquei — chapéu de aba com faixa, colete curto, calça de montaria, botas com esporas;</li>
 *   <li>II Cavaleiro das Planícies — sobretudo, cachecol que voa, cartucheira de bolas;</li>
 *   <li>III Mestre da Rotação — prata e verde, espirais gravadas que brilham;</li>
 *   <li>IV Peregrino Dourado — ouro, Retângulo Dourado no peito, capa longa.</li>
 * </ul>
 */
public enum SbOutfitTier {
    JOCKEY("jockey", 1, 1, new int[] {2, 5, 4, 2}, 0.0F, 13, Rarity.COMMON, 0xC9B48A, false,
            new double[][] {{15, 15, 0, 0}, {0, 0, 0.02, 0.02}, {0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}}),
    PLAINS("plains", 2, 3, new int[] {3, 7, 5, 3}, 0.75F, 20, Rarity.UNCOMMON, 0x7FC8FF, false,
            new double[][] {{15, 30, 15, 15}, {0, 0, 0.03, 0.03}, {0.04, 0, 0, 0.04}, {0, 0, 0, 0}, {0, 0, 0, 0}}),
    ROTATION("rotation", 3, 5, new int[] {3, 9, 7, 4}, 1.5F, 28, Rarity.RARE, 0x7FE0B0, true,
            new double[][] {{30, 45, 30, 30}, {0, 0.03, 0.03, 0.03}, {0.05, 0.05, 0, 0.05}, {0.04, 0, 0, 0}, {0, 0, 0, 1}}),
    SAINT("saint", 4, 8, new int[] {4, 11, 8, 5}, 3.0F, 37, Rarity.EPIC, 0xF2D27A, true,
            new double[][] {{45, 90, 45, 45}, {0, 0.04, 0.04, 0.04}, {0.10, 0.05, 0, 0.10}, {0.04, 0.04, 0, 0}, {0, 0, 0, 3}});

    /** Graus entregues ao subir de Círculo (o I vem no kit). */
    public static final SbOutfitTier[] REWARDS = {PLAINS, ROTATION, SAINT};
    public static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
            ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

    /** Bônus que a roupa dá (as linhas das tabelas, na ordem). */
    public enum Stat {
        FOCUS("focus", CoreRegistries.MAX_MANA, AttributeModifier.Operation.ADD_VALUE, false),
        SPEED("speed", Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, true),
        REGEN("regen", CoreRegistries.MANA_REGEN, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, true),
        POWER("power", CoreRegistries.SPELL_POWER, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, true),
        SAFE_FALL("safe_fall", Attributes.SAFE_FALL_DISTANCE, AttributeModifier.Operation.ADD_VALUE, false);

        private static final String STAT_KEY = "tooltip.isekai_mahou.steelball.outfit.stat.";

        public final String id;
        public final Holder<Attribute> attribute;
        public final AttributeModifier.Operation operation;
        public final boolean percent;

        Stat(String id, Holder<Attribute> attribute, AttributeModifier.Operation operation, boolean percent) {
            this.id = id;
            this.attribute = attribute;
            this.operation = operation;
            this.percent = percent;
        }

        public MutableComponent describe(double amount) {
            double shown = percent ? amount * 100 : amount;
            String v = shown == Math.rint(shown) ? String.valueOf((long) shown) : String.format(java.util.Locale.ROOT, "%.1f", shown);
            return Component.translatable(STAT_KEY + id, v);
        }
    }

    public record Bonus(Stat stat, double amount) {}

    private static final String TIER_KEY = "outfit.isekai_mahou.steelball.tier.";
    private static final String FLAVOR_KEY = "tooltip.isekai_mahou.steelball.outfit.flavor.";

    public final String id;
    public final int number;
    public final int circle;
    private final int[] defense;
    public final float toughness;
    public final int durability;
    public final Rarity rarity;
    public final int color;
    public final boolean glows;
    private final double[][] bonuses;

    SbOutfitTier(String id, int number, int circle, int[] defense, float toughness, int durability, Rarity rarity, int color,
            boolean glows, double[][] bonuses) {
        this.id = id;
        this.number = number;
        this.circle = circle;
        this.defense = defense;
        this.toughness = toughness;
        this.durability = durability;
        this.rarity = rarity;
        this.color = color;
        this.glows = glows;
        this.bonuses = bonuses;
    }

    private static int piece(ArmorItem.Type type) {
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

    public List<Bonus> bonuses(ArmorItem.Type type) {
        List<Bonus> out = new ArrayList<>();
        for (Stat s : Stat.values()) {
            double v = bonuses[s.ordinal()][piece(type)];
            if (v != 0) out.add(new Bonus(s, v));
        }
        return out;
    }

    public List<Bonus> totals() {
        List<Bonus> out = new ArrayList<>();
        for (Stat s : Stat.values()) {
            double sum = 0;
            for (double v : bonuses[s.ordinal()]) sum += v;
            if (sum != 0) out.add(new Bonus(s, sum));
        }
        return out;
    }

    /** {@code steelball_outfit_<grau>_<peça>}. */
    public String itemId(ArmorItem.Type type) {
        return "steelball_outfit_" + id + "_" + type.getName();
    }

    /** Material e textura vanilla de reserva ({@code textures/models/armor/<id>_layer_1.png}). */
    public String materialId() {
        return "steelball_outfit_" + id;
    }

    /** Modelo GeckoLib: {@code geo/item/armor/steelball_outfit_<grau>.geo.json}. */
    public ResourceLocation modelId() {
        return IsekaiMahou.id("armor/steelball_outfit_" + id);
    }

    public String flag() {
        return "steelball.armor." + id;
    }

    public MutableComponent displayName() {
        return Component.translatable(TIER_KEY + id);
    }

    /** Frase de ambientação do grau (dica das peças). */
    public MutableComponent flavor() {
        return Component.translatable(FLAVOR_KEY + id);
    }

    public ChatFormatting chatColor() {
        return switch (this) {
            case JOCKEY -> ChatFormatting.WHITE;
            case PLAINS -> ChatFormatting.YELLOW;
            case ROTATION -> ChatFormatting.AQUA;
            case SAINT -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    public String roman() {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> "IV";
        };
    }

    @Nullable
    public SbOutfitTier next() {
        int i = ordinal() + 1;
        return i < values().length ? values()[i] : null;
    }

    @Nullable
    public static SbOutfitTier byId(String id) {
        for (SbOutfitTier t : values()) if (t.id.equals(id)) return t;
        return null;
    }
}
