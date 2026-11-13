package com.renanpontes.isekaimahou.feature.gear_plus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.outfits.OutfitArmorItem;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * <b>Bônus de conjunto</b> (v1.0 RELEASE): vestir as 4 peças da MESMA família dá um bônus pequeno e temático.
 * <p>
 * A família é descoberta sem depender do módulo dono da armadura:
 * <ul>
 *   <li>trajes do módulo {@code outfits}: {@code outfit_<id>} (Mago, Bruxa... e os 10 avulsos), grau = I..IV;</li>
 *   <li>armaduras dos módulos de classe ({@code feature.class_<id>}): {@code class_<id>}; o grau vem do {@code tier()}
 *       (ou {@code gear()}) da peça, lido por reflexão uma vez por classe de item;</li>
 *   <li>materiais do vanilla ({@code vanilla_iron}...) e os avulsos do mod ({@code mat_mithril}...): grau único.</li>
 * </ul>
 * Graus misturados valem pelo MENOR (como o brilho do traje). Os números são baixos de propósito: subir de nível pesa
 * mais que item. As classes que já tinham um efeito próprio de traje completo (Cavaleiro, Ladino...) continuam com ele;
 * aqui entra só um atributo leve por cima.
 */
public final class SetBonuses {
    public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    public static final int PIECES = ARMOR_SLOTS.length;

    /** Uma linha do bônus: atributo, operação e o valor por grau (I..IV). */
    public record Line(Holder<Attribute> attribute, AttributeModifier.Operation op, double[] byRank) {
        public double at(int rank) {
            return byRank[Math.max(1, Math.min(byRank.length, rank)) - 1];
        }

        /** "+8% Poder Mágico", "+4 Vida Máxima". */
        public MutableComponent describe(int rank) {
            return GearText.attribute(attribute, op, at(rank));
        }
    }

    /** Conjunto completo vestido. */
    public record Active(String family, int rank) {}

    private static final Map<String, List<Line>> TABLE = new HashMap<>();
    /** Família de cada item ("" = nenhuma), guardada depois da 1ª consulta. */
    private static final Map<Item, String> FAMILY = new ConcurrentHashMap<>();
    /** Método do grau por classe de item (ausente = sem método). */
    private static final Map<Class<?>, java.util.Optional<Method>> TIER_METHOD = new ConcurrentHashMap<>();

    static {
        // ---- Trajes das 5 classes Mágicas
        put("outfit_mage", pct(CoreRegistries.SPELL_POWER, 0.03, 0.05, 0.07, 0.10));
        put("outfit_witch", pct(CoreRegistries.MANA_REGEN, 0.06, 0.09, 0.12, 0.18));
        put("outfit_warlock", pct(CoreRegistries.SPELL_POWER, 0.02, 0.03, 0.05, 0.07), flat(Attributes.MAX_HEALTH, 2, 4, 6, 8));
        put("outfit_druid", flat(Attributes.MAX_HEALTH, 4, 6, 8, 12), pct(CoreRegistries.MANA_REGEN, 0.03, 0.05, 0.07, 0.10));
        put("outfit_necromancer", flat(CoreRegistries.MAX_MANA, 30, 45, 60, 90), flat(Attributes.ARMOR_TOUGHNESS, 1, 1, 2, 2));
        // ---- Trajes avulsos (v0.9)
        put("outfit_warrior", flat(Attributes.ATTACK_DAMAGE, 0.5, 1, 1.5, 2));
        put("outfit_paladin", flat(Attributes.MAX_HEALTH, 4, 6, 8, 12));
        put("outfit_barbarian", flat(Attributes.ATTACK_DAMAGE, 0.5, 1, 1, 1.5), pct(Attributes.ATTACK_SPEED, 0.02, 0.03, 0.05, 0.07));
        put("outfit_monk", pct(Attributes.ATTACK_SPEED, 0.04, 0.06, 0.08, 0.12));
        put("outfit_knight", flat(Attributes.KNOCKBACK_RESISTANCE, 0.1, 0.15, 0.2, 0.3), flat(Attributes.ARMOR, 1, 1, 2, 2));
        put("outfit_ranger", pct(Attributes.MOVEMENT_SPEED, 0.03, 0.04, 0.06, 0.08));
        put("outfit_rogue", pct(Attributes.ATTACK_SPEED, 0.03, 0.05, 0.07, 0.10), pct(Attributes.SNEAKING_SPEED, 0.05, 0.10, 0.15, 0.20));
        put("outfit_bard", pct(CoreRegistries.MANA_REGEN, 0.05, 0.08, 0.11, 0.15));
        put("outfit_artificer", flat(CoreRegistries.MAX_MANA, 30, 45, 60, 90));
        put("outfit_hunter", pct(Attributes.MOVEMENT_SPEED, 0.02, 0.03, 0.05, 0.07), flat(Attributes.SAFE_FALL_DISTANCE, 1, 2, 3, 4));
        // ---- Armaduras dos módulos de classe
        put("class_smith", flat(Attributes.ARMOR_TOUGHNESS, 1, 2, 3, 4), flat(Attributes.ATTACK_DAMAGE, 0.5, 0.5, 1, 1.5));
        put("class_shinigami", pct(Attributes.ATTACK_SPEED, 0.03, 0.05, 0.07, 0.10), pct(Attributes.MOVEMENT_SPEED, 0.02, 0.03, 0.04, 0.05));
        put("class_quincy", flat(CoreRegistries.MAX_MANA, 30, 45, 60, 90), pct(CoreRegistries.MANA_REGEN, 0.03, 0.05, 0.07, 0.10));
        put("class_steelball", pct(CoreRegistries.MANA_REGEN, 0.04, 0.06, 0.08, 0.12), flat(Attributes.KNOCKBACK_RESISTANCE, 0.05, 0.1, 0.15, 0.2));
        // (estas já têm efeito próprio de traje completo no módulo da classe: aqui só um atributo leve)
        put("class_knight", flat(Attributes.MAX_HEALTH, 2, 4, 6, 8));
        put("class_rogue", pct(Attributes.MOVEMENT_SPEED, 0.02, 0.03, 0.04, 0.05));
        put("class_enchanter", flat(CoreRegistries.MAX_MANA, 20, 30, 45, 60));
        put("class_gambler", flat(Attributes.LUCK, 1, 1, 2, 2));
        put("class_gunslinger", pct(Attributes.MOVEMENT_SPEED, 0.02, 0.03, 0.04, 0.05));
        put("class_ranger", flat(Attributes.SAFE_FALL_DISTANCE, 1, 2, 3, 4), pct(Attributes.MOVEMENT_SPEED, 0.01, 0.02, 0.03, 0.04));
        // ---- Armaduras avulsas do mod
        put("mat_mithril", pct(Attributes.BLOCK_BREAK_SPEED, 0.10), flat(Attributes.LUCK, 1));
        put("mat_star", pct(CoreRegistries.SPELL_POWER, 0.06), flat(CoreRegistries.MAX_MANA, 45));
        put("mat_adamantite", flat(Attributes.ARMOR_TOUGHNESS, 2), flat(Attributes.KNOCKBACK_RESISTANCE, 0.1));
        // ---- Vanilla (bem leve)
        put("vanilla_leather", pct(Attributes.MOVEMENT_SPEED, 0.03));
        put("vanilla_chainmail", pct(Attributes.ATTACK_SPEED, 0.05));
        put("vanilla_iron", flat(Attributes.ARMOR, 1));
        put("vanilla_gold", flat(Attributes.LUCK, 1));
        put("vanilla_diamond", flat(Attributes.ARMOR_TOUGHNESS, 1));
        put("vanilla_netherite", flat(Attributes.KNOCKBACK_RESISTANCE, 0.1));
    }

    private static void put(String family, Line... lines) {
        TABLE.put(family, List.of(lines));
    }

    private static Line pct(Holder<Attribute> a, double... v) {
        return new Line(a, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, v);
    }

    private static Line flat(Holder<Attribute> a, double... v) {
        return new Line(a, AttributeModifier.Operation.ADD_VALUE, v);
    }

    /** Todas as famílias com bônus (para o comando de depuração). */
    public static List<String> families() {
        List<String> out = new ArrayList<>(TABLE.keySet());
        out.sort(null);
        return out;
    }

    public static List<Line> lines(String family) {
        return TABLE.getOrDefault(family, List.of());
    }

    public static MutableComponent familyName(String family) {
        return Component.translatable("gear_plus.isekai_mahou.set." + family);
    }

    /** Família da peça, ou null se ela não faz parte de um conjunto com bônus. */
    @Nullable
    public static String familyOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armor)) return null;
        String f = FAMILY.computeIfAbsent(armor, i -> {
            String found = resolve(armor);
            return found != null && TABLE.containsKey(found) ? found : "";
        });
        return f.isEmpty() ? null : f;
    }

    @Nullable
    private static String resolve(ArmorItem armor) {
        if (armor instanceof OutfitArmorItem outfit) return "outfit_" + outfit.set().id;
        String cls = armor.getClass().getName();
        int at = cls.indexOf(".feature.class_");
        if (at >= 0) {
            int from = at + ".feature.".length();
            int to = cls.indexOf('.', from);
            if (to > from) return cls.substring(from, to);
        }
        ResourceKey<ArmorMaterial> key = armor.getMaterial().unwrapKey().orElse(null);
        if (key == null) return null;
        String ns = key.location().getNamespace();
        if ("minecraft".equals(ns)) return "vanilla_" + key.location().getPath();
        if (IsekaiMahou.MODID.equals(ns)) return "mat_" + key.location().getPath();
        return null;
    }

    /** Grau da peça (1..4) para a tabela de bônus. Peças sem graus valem 1. */
    public static int rankOf(ItemStack stack) {
        return Math.min(4, gradeOf(stack));
    }

    /**
     * Grau da peça SEM o teto da tabela de bônus (1 = a do kit; a ordem é a do enum de graus do módulo dono). Serve para
     * comparar duas peças do mesmo conjunto: a recuperação do kit devolve a de maior grau que o jogador já carregou.
     */
    public static int gradeOf(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof OutfitArmorItem outfit) return outfit.tier().ordinal() + 1;
        Method m = TIER_METHOD.computeIfAbsent(item.getClass(), SetBonuses::findTierMethod).orElse(null);
        if (m == null) return 1;
        try {
            Object tier = m.invoke(item);
            return tier instanceof Enum<?> e ? Math.max(1, e.ordinal() + 1) : 1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1;
        }
    }

    /** As armaduras de classe expõem o grau por {@code tier()} (ou {@code gear()} no Ladino): um enum em ordem. */
    private static java.util.Optional<Method> findTierMethod(Class<?> c) {
        if (!c.getName().contains(".feature.class_")) return java.util.Optional.empty();
        for (String name : new String[] {"tier", "gear"}) {
            try {
                Method m = c.getMethod(name);
                if (m.getReturnType().isEnum()) return java.util.Optional.of(m);
            } catch (NoSuchMethodException | SecurityException ignored) {
                // tenta o próximo nome
            }
        }
        return java.util.Optional.empty();
    }

    /** Conjunto completo vestido (4 peças da mesma família), ou null. */
    @Nullable
    public static Active active(LivingEntity entity) {
        String family = null;
        int rank = 4;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack worn = entity.getItemBySlot(slot);
            String f = familyOf(worn);
            if (f == null || (family != null && !family.equals(f))) return null;
            family = f;
            rank = Math.min(rank, rankOf(worn));
        }
        return family == null ? null : new Active(family, rank);
    }

    /** Quantas peças da família a entidade veste e o menor grau entre elas ({@code [peças, grau]}). */
    public static int[] worn(LivingEntity entity, String family) {
        int n = 0, rank = 4;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack worn = entity.getItemBySlot(slot);
            if (!family.equals(familyOf(worn))) continue;
            n++;
            rank = Math.min(rank, rankOf(worn));
        }
        return new int[] {n, n == 0 ? 1 : rank};
    }

    private SetBonuses() {}
}
