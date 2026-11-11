package com.renanpontes.isekaimahou.feature.outfits;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;

/**
 * Números dos trajes por CATEGORIA de classe (v0.9). As Mágicas continuam exatamente com os valores de {@link OutfitTier};
 * as Guerreiras são armaduras de verdade (mais defesa, resistência e vida, um pouco de dano corpo a corpo) e as À Distância
 * são leves (menos defesa, mais velocidade, passo silencioso e queda segura). Todas dão um pouco do recurso da classe
 * (Mana/Vigor/Foco usam o mesmo atributo de mana máximo).
 * <p>v1.0 (inflação de números, V10_DESIGN §0.1): defesa ×1,5 e resistência maior em todas as categorias; o recurso
 * máximo das tabelas (escala da v0.8) é multiplicado por {@link ResourceTraining#MANA_SCALE} (×3, como a mana base) e a
 * vida extra acompanha a vida base dobrada ({@link #HEALTH_SCALE}). O teto do atributo de armadura é 30 e o da
 * resistência é 20: o grau IV das Físicas encosta nos dois.
 * <p>Conjunto completo, por grau I / II / III / IV:
 * <ul>
 *   <li>Mágicas: defesa 11/19/25/28, resistência 2/4/10/16 (+ os bônus de {@link OutfitTier});</li>
 *   <li>Físicas: defesa 18/23/28/30, resistência 4/8/12/20, +30/60/105/180 Vigor, +4/8/12/20 de vida,
 *   +0/0,5/1/2 de dano corpo a corpo, repulsão 0/0/0,2/0,4;</li>
 *   <li>Destreza: defesa 14/19/24/27, resistência 2/4/8/14, +30/75/135/225 Foco, +4/6/9/12% de velocidade,
 *   +0/10/15/20% andando agachado e até +3 blocos de queda segura.</li>
 * </ul>
 */
public final class OutfitStats {
    /** Vida extra dos trajes acompanha a vida base do jogador (40 na v1.0 = 2× os 20 do vanilla). */
    public static final double HEALTH_SCALE = Progression.BASE_PLAYER_HEALTH / 20.0;
    /** Atributos que os trajes podem dar. O id entra no id do modificador ({@code outfit.<peça>.<id>}). */
    public enum Stat {
        RESOURCE("max_mana", CoreRegistries.MAX_MANA, AttributeModifier.Operation.ADD_VALUE, false),
        REGEN("mana_regen", CoreRegistries.MANA_REGEN, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, true),
        POWER("spell_power", CoreRegistries.SPELL_POWER, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, true),
        HEALTH("max_health", Attributes.MAX_HEALTH, AttributeModifier.Operation.ADD_VALUE, false),
        MELEE("attack_damage", Attributes.ATTACK_DAMAGE, AttributeModifier.Operation.ADD_VALUE, false),
        SPEED("movement_speed", Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, true),
        SNEAK("sneaking_speed", Attributes.SNEAKING_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, true),
        SAFE_FALL("safe_fall_distance", Attributes.SAFE_FALL_DISTANCE, AttributeModifier.Operation.ADD_VALUE, false);

        public final String id;
        public final Holder<Attribute> attribute;
        public final AttributeModifier.Operation operation;
        /** Mostrado como porcentagem (0.05 = 5%). */
        public final boolean percent;
        /** Chave do texto do bônus ({@code outfit.isekai_mahou.stat.<id>}). */
        private final String key;

        Stat(String id, Holder<Attribute> attribute, AttributeModifier.Operation operation, boolean percent) {
            this.id = id;
            this.attribute = attribute;
            this.operation = operation;
            this.percent = percent;
            this.key = "outfit.isekai_mahou.stat." + id;
        }

        /** "+5 Vigor", "+12% Velocidade"... (o recurso aparece com o nome e a cor da classe). */
        public MutableComponent describe(double amount, MagicPath path) {
            String value = fmt(percent ? amount * 100 : amount);
            return switch (this) {
                case RESOURCE -> Component.translatable("outfit.isekai_mahou.stat.resource", value, path.resource().displayName());
                case REGEN -> Component.translatable("outfit.isekai_mahou.stat.regen", value, path.resource().displayName());
                default -> Component.translatable(key, value);
            };
        }
    }

    /** Bônus de uma peça (ou do conjunto inteiro). */
    public record Bonus(Stat stat, double amount) {}

    /** Tabela de uma categoria: [grau][peça] na ordem capacete, peitoral, calças, botas. */
    private record Profile(int[][] defense, float[] toughness, float[] knockback, int[] durability, int enchantability,
            Map<Stat, double[][]> bonuses) {}

    /** Ordem das colunas das tabelas (igual a OutfitRegistries.TYPES, repetida para não depender da ordem de carga). */
    private static final ArmorItem.Type[] ORDER = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
            ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

    private static final Map<MagicPath.Category, Profile> PROFILES = new EnumMap<>(MagicPath.Category.class);

    static {
        PROFILES.put(MagicPath.Category.MAGIC, magic());
        PROFILES.put(MagicPath.Category.PHYSICAL, new Profile(
                // v0.8: {2,5,4,1} {2,6,5,2} {3,7,6,3} {3,9,7,3} (12/15/19/22) → ×1,5 (o IV no teto de 30)
                new int[][] {{3, 7, 6, 2}, {3, 9, 8, 3}, {4, 11, 9, 4}, {5, 12, 9, 4}},
                new float[] {1.0F, 2.0F, 3.0F, 5.0F},
                new float[] {0.0F, 0.0F, 0.05F, 0.1F},
                new int[] {15, 24, 33, 40}, 15,
                bonuses(
                        Stat.RESOURCE, new double[][] {{0, 6, 4, 0}, {4, 8, 6, 2}, {6, 14, 10, 5}, {10, 24, 16, 10}},
                        Stat.HEALTH, new double[][] {{0, 2, 0, 0}, {0, 2, 2, 0}, {0, 4, 2, 0}, {2, 4, 4, 0}},
                        Stat.MELEE, new double[][] {{0, 0, 0, 0}, {0, 0.5, 0, 0}, {0, 0.5, 0.5, 0}, {0.5, 1.0, 0.5, 0}},
                        Stat.REGEN, new double[][] {{0, 0, 0, 0}, {0, 0, 0, 0.05}, {0, 0, 0.05, 0.05}, {0, 0, 0.05, 0.10}},
                        Stat.POWER, new double[][] {{0, 0, 0, 0}, {0, 0, 0, 0}, {0.03, 0, 0, 0}, {0.03, 0.03, 0, 0}})));
        PROFILES.put(MagicPath.Category.DEXTERITY, new Profile(
                // v0.8: {1,4,3,1} {2,5,4,2} {2,6,5,3} {3,7,6,3} (9/13/16/19) → ×1,5
                new int[][] {{2, 6, 4, 2}, {3, 7, 6, 3}, {3, 9, 8, 4}, {4, 11, 8, 4}},
                new float[] {0.5F, 1.0F, 2.0F, 3.5F},
                new float[] {0.0F, 0.0F, 0.0F, 0.0F},
                new int[] {13, 20, 28, 37}, 20,
                bonuses(
                        Stat.RESOURCE, new double[][] {{5, 5, 0, 0}, {5, 10, 5, 5}, {10, 15, 10, 10}, {15, 30, 15, 15}},
                        Stat.SPEED, new double[][] {{0, 0, 0.02, 0.02}, {0, 0, 0.03, 0.03}, {0, 0.03, 0.03, 0.03}, {0, 0.04, 0.04, 0.04}},
                        Stat.REGEN, new double[][] {{0, 0, 0, 0}, {0.04, 0, 0, 0.04}, {0.05, 0.05, 0, 0.05}, {0.10, 0.05, 0, 0.10}},
                        Stat.POWER, new double[][] {{0, 0, 0, 0}, {0, 0, 0, 0}, {0.04, 0, 0, 0}, {0.04, 0.04, 0, 0}},
                        Stat.SNEAK, new double[][] {{0, 0, 0, 0}, {0, 0, 0.10, 0}, {0, 0, 0.15, 0}, {0, 0, 0.20, 0}},
                        Stat.SAFE_FALL, new double[][] {{0, 0, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 1}, {0, 0, 0, 3}})));
    }

    /** As Mágicas usam os números de sempre (OutfitTier), copiados para o mesmo formato. */
    private static Profile magic() {
        OutfitTier[] tiers = OutfitTier.values();
        int[][] defense = new int[tiers.length][4];
        float[] toughness = new float[tiers.length];
        float[] knockback = new float[tiers.length];
        int[] durability = new int[tiers.length];
        double[][] mana = new double[tiers.length][4];
        double[][] regen = new double[tiers.length][4];
        double[][] power = new double[tiers.length][4];
        for (OutfitTier t : tiers) {
            int i = t.ordinal();
            toughness[i] = t.toughness;
            knockback[i] = t.knockbackResistance;
            durability[i] = t.durabilityFactor;
            for (int p = 0; p < 4; p++) {
                ArmorItem.Type type = ORDER[p];
                defense[i][p] = t.defense(type);
                OutfitTier.Bonus b = t.bonus(type);
                mana[i][p] = b.mana();
                regen[i][p] = b.regen();
                power[i][p] = b.power();
            }
        }
        return new Profile(defense, toughness, knockback, durability, 25,
                bonuses(Stat.RESOURCE, mana, Stat.REGEN, regen, Stat.POWER, power));
    }

    private static Map<Stat, double[][]> bonuses(Object... pairs) {
        Map<Stat, double[][]> m = new EnumMap<>(Stat.class);
        for (int i = 0; i < pairs.length; i += 2) m.put((Stat) pairs[i], (double[][]) pairs[i + 1]);
        return m;
    }

    private static Profile profile(OutfitSet set) {
        return PROFILES.get(set.category());
    }

    private static int piece(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 0;
            case CHESTPLATE, BODY -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    public static int defense(OutfitSet set, OutfitTier tier, ArmorItem.Type type) {
        return profile(set).defense[tier.ordinal()][piece(type)];
    }

    public static int totalDefense(OutfitSet set, OutfitTier tier) {
        int sum = 0;
        for (int d : profile(set).defense[tier.ordinal()]) sum += d;
        return sum;
    }

    /** Resistência (toughness) POR PEÇA. */
    public static float toughness(OutfitSet set, OutfitTier tier) {
        return profile(set).toughness[tier.ordinal()];
    }

    /** Resistência a repulsão POR PEÇA. */
    public static float knockback(OutfitSet set, OutfitTier tier) {
        return profile(set).knockback[tier.ordinal()];
    }

    public static int durabilityFactor(OutfitSet set, OutfitTier tier) {
        return profile(set).durability[tier.ordinal()];
    }

    public static int enchantability(OutfitSet set) {
        return profile(set).enchantability;
    }

    /**
     * Valor real de um bônus das tabelas (que estão na escala da v0.8): o recurso máximo ×{@link ResourceTraining#MANA_SCALE}
     * e a vida ×{@link #HEALTH_SCALE}; porcentagens e o resto ficam iguais.
     */
    public static double scaled(Stat stat, double tableValue) {
        return switch (stat) {
            case RESOURCE -> tableValue * ResourceTraining.MANA_SCALE;
            case HEALTH -> tableValue * HEALTH_SCALE;
            default -> tableValue;
        };
    }

    /** Bônus de uma peça (só os diferentes de zero), já na escala da v1.0. */
    public static List<Bonus> bonuses(OutfitSet set, OutfitTier tier, ArmorItem.Type type) {
        List<Bonus> out = new ArrayList<>();
        for (Map.Entry<Stat, double[][]> e : profile(set).bonuses.entrySet()) {
            double v = e.getValue()[tier.ordinal()][piece(type)];
            if (v != 0) out.add(new Bonus(e.getKey(), scaled(e.getKey(), v)));
        }
        return out;
    }

    /** Soma do conjunto completo (4 peças), na ordem dos {@link Stat}. */
    public static List<Bonus> totals(OutfitSet set, OutfitTier tier) {
        List<Bonus> out = new ArrayList<>();
        for (Map.Entry<Stat, double[][]> e : profile(set).bonuses.entrySet()) {
            double sum = 0;
            for (double v : e.getValue()[tier.ordinal()]) sum += v;
            if (sum != 0) out.add(new Bonus(e.getKey(), scaled(e.getKey(), sum)));
        }
        return out;
    }

    /** 15.000000000000002 (soma de frações) vira "15"; 7.5 continua "7.5". */
    public static String fmt(double v) {
        double r = Math.round(v * 10.0) / 10.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.format(java.util.Locale.ROOT, "%.1f", r);
    }

    private OutfitStats() {}
}
