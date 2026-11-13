package com.renanpontes.isekaimahou.feature.talents;

import java.util.Locale;
import java.util.Set;

import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Nomes e descrições dos talentos (lado comum: só monta {@link Component}s). A descrição de um nó NÃO é escrita à mão:
 * sai dos efeitos dele ("+5% Poder Mágico"), então JSON e texto nunca ficam diferentes.
 */
public final class TalentText {
    /** Atributos somados com {@code add_value} que na verdade são multiplicadores/frações (mostra em %). */
    private static final Set<String> PERCENT_ATTRIBUTES = Set.of("isekai_mahou:spell_power", "isekai_mahou:mana_regen",
            "minecraft:generic.knockback_resistance");
    /** Atributos que a tela não lista sozinhos (são só o suporte de uma passiva: a absorção máxima da Barreira). */
    public static final Set<String> HIDDEN_ATTRIBUTES = Set.of("minecraft:generic.max_absorption");

    public static MutableComponent nodeName(String classId, String node) {
        return Component.translatable("talent.isekai_mahou." + classId + "." + node);
    }

    public static MutableComponent branchName(String classId, String branch) {
        return Component.translatable("talent.isekai_mahou." + classId + ".branch." + branch);
    }

    public static MutableComponent subclassName(String id) {
        return Component.translatable("subclass.isekai_mahou." + id);
    }

    public static MutableComponent subclassDescription(String id) {
        return Component.translatable("subclass.isekai_mahou." + id + ".desc");
    }

    /** Como a especialização joga, em uma linha simples (tela de escolha). */
    public static MutableComponent subclassPlay(String id) {
        return Component.translatable("subclass.isekai_mahou." + id + ".play");
    }

    public static MutableComponent kindName(TalentTree.Kind kind) {
        return Component.translatable("screen.isekai_mahou.talents.kind." + kind.name().toLowerCase(Locale.ROOT));
    }

    /** Linha de um efeito com o valor total de {@code rank} níveis (ex.: "+3% Poder Mágico"). */
    public static MutableComponent describe(TalentTree.Effect e, int rank) {
        return describe(e, (double) Math.max(1, rank));
    }

    /** Linha de um efeito multiplicado por {@code scale} "níveis cheios" (ver {@link TalentTree.Node#scale}). */
    public static MutableComponent describe(TalentTree.Effect e, double scale) {
        double total = e.amount() * scale;
        if (e.isAttribute()) {
            ResourceLocation id = e.attribute();
            boolean percent = e.op() != AttributeModifier.Operation.ADD_VALUE || PERCENT_ATTRIBUTES.contains(id.toString());
            Attribute attr = BuiltInRegistries.ATTRIBUTE.getOptional(id).orElse(null);
            Component name = attr != null ? Component.translatable(attr.getDescriptionId()) : Component.literal(id.toString());
            return Component.translatable("talent.isekai_mahou.fx.attribute", percent ? percent(total) : flat(total), name);
        }
        return describeStat(e.stat(), total);
    }

    /** Linha de uma passiva somada (painel de "bônus ativos"). */
    public static MutableComponent describeStat(String key, double total) {
        if (key.startsWith(TalentStats.SPELL_DAMAGE_PREFIX)) {
            Element el = Element.byId(key.substring(TalentStats.SPELL_DAMAGE_PREFIX.length()));
            Component name = el != null ? el.displayName() : Component.literal(key);
            return Component.translatable("talent.isekai_mahou.stat.spell_damage", percent(total), name);
        }
        if (key.startsWith(TalentStats.ON_HIT_PREFIX)) {
            return Component.translatable("talent.isekai_mahou.stat.on_hit", percent(total),
                    Component.translatable("talent.isekai_mahou.onhit." + key.substring(TalentStats.ON_HIT_PREFIX.length())));
        }
        if (key.startsWith(TalentStats.ON_STRUCK_PREFIX)) {
            return Component.translatable("talent.isekai_mahou.stat.on_struck", percent(total),
                    Component.translatable("talent.isekai_mahou.onhit." + key.substring(TalentStats.ON_STRUCK_PREFIX.length())));
        }
        String value = TalentStats.FLAT.contains(key) ? flat(total) : percent(total);
        if (TalentStats.PLAIN.contains(key) && value.startsWith("+")) value = value.substring(1);
        return Component.translatable("talent.isekai_mahou.stat." + key, value);
    }

    /** Valor de uma passiva sem sinal, para mostrar um teto ("25%", "5"). */
    public static String plainValue(String key, double v) {
        String value = TalentStats.FLAT.contains(key) ? flat(v) : percent(v);
        return value.startsWith("+") ? value.substring(1) : value;
    }

    private static String percent(double v) {
        return signed(v * 100.0) + "%";
    }

    private static String flat(double v) {
        return signed(v);
    }

    /** "+5", "+2.5", "-3" — no máximo uma casa, sem zero sobrando. */
    private static String signed(double v) {
        double r = Math.round(v * 10.0) / 10.0;
        String s = r == Math.rint(r) ? String.valueOf((long) r) : String.format(Locale.ROOT, "%.1f", r);
        return r >= 0 ? "+" + s : s;
    }

    private TalentText() {}
}
