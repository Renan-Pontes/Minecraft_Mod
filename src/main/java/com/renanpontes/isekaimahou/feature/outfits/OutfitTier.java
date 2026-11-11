package com.renanpontes.isekaimahou.feature.outfits;

import java.util.EnumMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Rarity;

/**
 * Graus dos trajes de classe (v0.7). O I (Aprendiz) são as roupas antigas, com os ids antigos; os outros ganham
 * id {@code <classe>_<grau>_<peça>}, material, modelo 3D e paleta próprios. Os números ficam todos aqui:
 * <ul>
 *   <li>defesa por peça (capacete, peitoral, calças, botas) — total 11 / 19 / 25 / 28 (v1.0: ×1,5 da v0.8, que era
 *   7 / 13 / 17 / 20; o IV fica abaixo dos 30 do teto do atributo para as Físicas continuarem as mais protegidas);</li>
 *   <li>resistência (toughness) por peça 0,5 / 1 / 2,5 / 4 (v0.8: 0 / 0,5 / 1,5 / 3) e resistência a repulsão só no IV;</li>
 *   <li>bônus mágicos por peça (mana máximo, regeneração, poder) — conjunto completo, na escala da v0.8:
 *   +20 mana · +5% regen | +40 · +15% · +6% poder | +80 · +30% · +12% | +150 · +50% · +20%. O mana máximo é
 *   multiplicado pela inflação do recurso ({@code ResourceTraining.MANA_SCALE}, ×3) em {@link OutfitStats}: a
 *   proporção roupa/mana da v0.8 se mantém.</li>
 * </ul>
 * Estes são os números das 5 classes MÁGICAS (a reforma entre elas é só aparência). Guerreiras e À Distância usam
 * as tabelas próprias de {@link OutfitStats}; o Círculo de entrega, a raridade e a cor do grau valem para todas.
 */
public enum OutfitTier {
    //          id           num  círc  defesa {cab, peito, pernas, pés}  tough  repulsão durab. raridade        cor
    APPRENTICE("apprentice", 1, 1, new int[] {2, 4, 3, 2}, 0.5F, 0.0F, 13, Rarity.COMMON, 0xC9B48A,
            bonuses(new double[] {5, 0, 0}, new double[] {10, 0, 0}, new double[] {5, 0, 0}, new double[] {0, 0.05, 0})),
    ADEPT("adept", 2, 3, new int[] {3, 7, 6, 3}, 1.0F, 0.0F, 20, Rarity.UNCOMMON, 0x7FC8FF,
            bonuses(new double[] {0, 0, 0.03}, new double[] {25, 0, 0.03}, new double[] {15, 0.08, 0}, new double[] {0, 0.07, 0})),
    MASTER("master", 3, 5, new int[] {4, 9, 8, 4}, 2.5F, 0.0F, 28, Rarity.RARE, 0xFFC94A,
            bonuses(new double[] {10, 0, 0.05}, new double[] {45, 0, 0.07}, new double[] {25, 0.15, 0}, new double[] {0, 0.15, 0})),
    ARCHMAGE("archmage", 4, 8, new int[] {4, 11, 9, 4}, 4.0F, 0.1F, 37, Rarity.EPIC, 0xD58CFF,
            bonuses(new double[] {20, 0, 0.08}, new double[] {80, 0, 0.12}, new double[] {50, 0.25, 0}, new double[] {0, 0.25, 0}));

    /** Graus entregues ao subir de Círculo (o I vem no kit inicial). */
    public static final OutfitTier[] REWARDS = {ADEPT, MASTER, ARCHMAGE};

    public final String id;
    /** Número do grau (I..IV). */
    public final int number;
    /** Círculo em que o conjunto é entregue (e o recomendado para usá-lo). */
    public final int circle;
    private final int[] defense;
    public final float toughness;
    public final float knockbackResistance;
    public final int durabilityFactor;
    public final Rarity rarity;
    /** Cor de destaque (toast, dica, partículas). */
    public final int color;
    private final Map<ArmorItem.Type, Bonus> bonuses;
    private final String nameKey;

    OutfitTier(String id, int number, int circle, int[] defense, float toughness, float knockbackResistance,
            int durabilityFactor, Rarity rarity, int color, Map<ArmorItem.Type, Bonus> bonuses) {
        this.id = id;
        this.number = number;
        this.circle = circle;
        this.defense = defense;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.durabilityFactor = durabilityFactor;
        this.rarity = rarity;
        this.color = color;
        this.bonuses = bonuses;
        this.nameKey = "outfit.isekai_mahou.tier." + id;
    }

    /** Bônus mágico de uma peça: mana máximo (soma), regeneração e poder (fração da base: 0.08 = +8%). */
    public record Bonus(double mana, double regen, double power) {}

    private static Map<ArmorItem.Type, Bonus> bonuses(double[] helmet, double[] chest, double[] legs, double[] boots) {
        Map<ArmorItem.Type, Bonus> m = new EnumMap<>(ArmorItem.Type.class);
        m.put(ArmorItem.Type.HELMET, new Bonus(helmet[0], helmet[1], helmet[2]));
        m.put(ArmorItem.Type.CHESTPLATE, new Bonus(chest[0], chest[1], chest[2]));
        m.put(ArmorItem.Type.LEGGINGS, new Bonus(legs[0], legs[1], legs[2]));
        m.put(ArmorItem.Type.BOOTS, new Bonus(boots[0], boots[1], boots[2]));
        return m;
    }

    public int defense(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> defense[0];
            case CHESTPLATE, BODY -> defense[1];
            case LEGGINGS -> defense[2];
            case BOOTS -> defense[3];
        };
    }

    public Bonus bonus(ArmorItem.Type type) {
        return bonuses.getOrDefault(type, new Bonus(0, 0, 0));
    }

    /** Grau I mantém os ids antigos ({@code <classe>_<peça>}); os outros têm o grau no meio. */
    public String itemId(OutfitSet set, ArmorItem.Type type) {
        return this == APPRENTICE ? set.id + "_" + type.getName() : set.id + "_" + id + "_" + type.getName();
    }

    /** Id do material e da textura vanilla de reserva ({@code textures/models/armor/<id>_layer_1.png}). */
    public String materialId(OutfitSet set) {
        return this == APPRENTICE ? "outfit_" + set.id : "outfit_" + set.id + "_" + id;
    }

    /** Flag gravada quando o conjunto deste grau já foi entregue (uma vez por jogador). */
    public String flag() {
        return "gear.armor." + id;
    }

    @Nullable
    public OutfitTier next() {
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
        return Component.translatable(nameKey);
    }

    /** Mesma cor da raridade do item (o nome da peça já aparece nessa cor). */
    public ChatFormatting chatColor() {
        return switch (this) {
            case APPRENTICE -> ChatFormatting.WHITE;
            case ADEPT -> ChatFormatting.YELLOW;
            case MASTER -> ChatFormatting.AQUA;
            case ARCHMAGE -> ChatFormatting.LIGHT_PURPLE;
        };
    }

    @Nullable
    public static OutfitTier byId(String id) {
        for (OutfitTier t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }
}
