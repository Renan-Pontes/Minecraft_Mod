package com.renanpontes.isekaimahou.feature.class_rogue;

import java.util.ArrayList;
import java.util.List;

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
 * Os 4 graus do traje do Ladino ("Traje das Sombras"): I no kit inicial, II no Círculo 3, III no Círculo 5 e IV no
 * Círculo 8 (mesmos Círculos dos trajes do módulo outfits). Couro e pano leves: menos defesa que uma armadura de
 * placas, mas velocidade, passo silencioso, Vigor e chance de crítico (o traje completo soma ao crítico do Ladino).
 * <p>Conjunto completo, por grau I / II / III / IV:
 * defesa 14/18/22/26 · resistência 2/4/8/12 · +30/72/132/216 Vigor · +4/6/8/10% velocidade · +10/15/20/30% agachado
 * · +0/0,5/1/2 dano corpo a corpo · +0/0/4/8% poder · +3/5/7/10% de crítico (passiva da classe).
 */
public enum RogueGear {
    //  id   num círc  defesa {cab, peito, pernas, pés}  tough  repulsão durab. raridade        cor      crítico
    T1("t1", 1, 1, new int[] {2, 6, 4, 2}, 0.5F, 0.0F, 15, Rarity.COMMON, 0xB9A7D6, 0.03F, false),
    T2("t2", 2, 3, new int[] {3, 7, 6, 2}, 1.0F, 0.0F, 22, Rarity.UNCOMMON, 0x8E7BFF, 0.05F, true),
    T3("t3", 3, 5, new int[] {3, 9, 7, 3}, 2.0F, 0.0F, 30, Rarity.RARE, 0xC08AFF, 0.07F, true),
    T4("t4", 4, 8, new int[] {4, 10, 8, 4}, 3.0F, 0.05F, 38, Rarity.EPIC, 0xF2ECFF, 0.10F, true);

    /** Ordem das peças (cabeça → pés), usada no kit, na aba criativa e na entrega. */
    public static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
            ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
    /** Graus entregues ao subir de Círculo (o I vem no kit). */
    public static final RogueGear[] REWARDS = {T2, T3, T4};

    public final String id;
    public final int number;
    public final int circle;
    private final int[] defense;
    public final float toughness;
    public final float knockback;
    public final int durability;
    public final Rarity rarity;
    public final int color;
    /** Chance de crítico extra com o traje COMPLETO (o grau da peça mais simples vale). */
    public final float critBonus;
    /** Tem textura que brilha (glowmask). */
    public final boolean glows;

    RogueGear(String id, int number, int circle, int[] defense, float toughness, float knockback, int durability,
              Rarity rarity, int color, float critBonus, boolean glows) {
        this.id = id;
        this.number = number;
        this.circle = circle;
        this.defense = defense;
        this.toughness = toughness;
        this.knockback = knockback;
        this.durability = durability;
        this.rarity = rarity;
        this.color = color;
        this.critBonus = critBonus;
        this.glows = glows;
    }

    /** Atributos extras do traje. O id entra no id do modificador ({@code rogue_shade.<peça>.<id>}). */
    public enum Stat {
        RESOURCE("max_mana", CoreRegistries.MAX_MANA, AttributeModifier.Operation.ADD_VALUE),
        SPEED("movement_speed", Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
        SNEAK("sneaking_speed", Attributes.SNEAKING_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
        MELEE("attack_damage", Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
        POWER("spell_power", CoreRegistries.SPELL_POWER, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        public final String id;
        public final Holder<Attribute> attribute;
        public final AttributeModifier.Operation operation;

        Stat(String id, Holder<Attribute> attribute, AttributeModifier.Operation operation) {
            this.id = id;
            this.attribute = attribute;
            this.operation = operation;
        }
    }

    public record Bonus(Stat stat, double amount) {}

    /** [grau][peça] na ordem capacete, peitoral, calças, botas. */
    private static final double[][][] TABLE = {
            // RESOURCE
            {{6, 14, 10, 0}, {14, 30, 20, 8}, {26, 54, 36, 16}, {42, 90, 56, 28}},
            // SPEED
            {{0, 0, 0.02, 0.02}, {0, 0, 0.03, 0.03}, {0, 0.02, 0.03, 0.03}, {0, 0.02, 0.04, 0.04}},
            // SNEAK
            {{0, 0, 0.10, 0}, {0, 0, 0.10, 0.05}, {0, 0, 0.12, 0.08}, {0, 0, 0.18, 0.12}},
            // MELEE
            {{0, 0, 0, 0}, {0, 0.5, 0, 0}, {0, 0.5, 0.5, 0}, {0.5, 1.0, 0.5, 0}},
            // POWER
            {{0, 0, 0, 0}, {0, 0, 0, 0}, {0.04, 0, 0, 0}, {0.04, 0.04, 0, 0}}};

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

    /** Bônus de uma peça (só os diferentes de zero). */
    public List<Bonus> bonuses(ArmorItem.Type type) {
        List<Bonus> out = new ArrayList<>();
        for (Stat s : Stat.values()) {
            double v = TABLE[s.ordinal()][ordinal()][piece(type)];
            if (v != 0) out.add(new Bonus(s, v));
        }
        return out;
    }

    /** Soma do conjunto completo. */
    public double total(Stat s) {
        double sum = 0;
        for (double v : TABLE[s.ordinal()][ordinal()]) sum += v;
        return sum;
    }

    /** Id do item: {@code rogue_shade_<grau>_<peça>}. */
    public String itemId(ArmorItem.Type type) {
        return "rogue_shade_" + id + "_" + type.getName();
    }

    /** Id do material e do modelo 3D ({@code geo/item/armor/rogue_shade_<grau>.geo.json}). */
    public String materialId() {
        return "rogue_shade_" + id;
    }

    /** Flag gravada quando o conjunto deste grau já foi entregue (a mesma convenção dos trajes do outfits). */
    public String flag() {
        return switch (this) {
            case T1 -> "gear.armor.apprentice";
            case T2 -> "gear.armor.adept";
            case T3 -> "gear.armor.master";
            case T4 -> "gear.armor.archmage";
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

    public MutableComponent displayName() {
        return Component.translatable(RogueKeys.k("class_rogue.isekai_mahou.gear.", id));
    }

    public ChatFormatting chatColor() {
        return switch (this) {
            case T1 -> ChatFormatting.WHITE;
            case T2 -> ChatFormatting.YELLOW;
            case T3 -> ChatFormatting.AQUA;
            case T4 -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    public static String fmt(double v) {
        double r = Math.round(v * 10.0) / 10.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.format(java.util.Locale.ROOT, "%.1f", r);
    }
}
