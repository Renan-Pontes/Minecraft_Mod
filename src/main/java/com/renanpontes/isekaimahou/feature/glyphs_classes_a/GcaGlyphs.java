package com.renanpontes.isekaimahou.feature.glyphs_classes_a;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.resources.ResourceLocation;

/**
 * v1.4 — os 16 glifos de CLASSE do módulo: 1 aumento + 1 gatilho para Mago, Bruxa, Bruxo, Druida, Necromante, Ferreiro,
 * Ladino e Cavaleiro. Todos são {@code origin(<classe>)}: a própria classe aprende pela progressão dela
 * ({@link GcaUnlocks}); as outras só por Lição escrita por alguém da classe (regra do núcleo para glifos de origem).
 * <ul>
 *   <li>Aumentos: o comportamento fica em {@link GcaAugments} (eventos de conjuração e de dano de magia).</li>
 *   <li>Gatilhos: reativos (vão no começo da receita; a tecla do slot não conjura) — {@link GcaTriggers} detecta a
 *       condição e {@link GcaTriggerEngine} conjura o slot sozinho.</li>
 * </ul>
 * Os ids NUNCA mudam (magias salvas guardam os ids).
 */
public final class GcaGlyphs {
    // ---------------- Aumentos ----------------
    public static final ResourceLocation MAGE_METAMAGIC = IsekaiMahou.id("mage_metamagic");
    public static final ResourceLocation WITCH_INFUSION = IsekaiMahou.id("witch_potion_infusion");
    public static final ResourceLocation WARLOCK_PACT = IsekaiMahou.id("warlock_blood_pact");
    public static final ResourceLocation DRUID_OVERGROWTH = IsekaiMahou.id("druid_overgrowth");
    public static final ResourceLocation NECRO_CHORUS = IsekaiMahou.id("necro_dead_chorus");
    public static final ResourceLocation SMITH_EDGE = IsekaiMahou.id("smith_forged_edge");
    public static final ResourceLocation ROGUE_BEHIND = IsekaiMahou.id("rogue_from_behind");
    public static final ResourceLocation KNIGHT_CHALLENGE = IsekaiMahou.id("knight_challenge");

    // ---------------- Gatilhos ----------------
    public static final ResourceLocation MAGE_WEAVE = IsekaiMahou.id("mage_trigger_weave");
    public static final ResourceLocation WITCH_DRINK = IsekaiMahou.id("witch_trigger_potion");
    public static final ResourceLocation WARLOCK_CURSED = IsekaiMahou.id("warlock_trigger_cursed");
    public static final ResourceLocation DRUID_ROOTED = IsekaiMahou.id("druid_trigger_rooted");
    public static final ResourceLocation NECRO_MINION = IsekaiMahou.id("necro_trigger_minion_death");
    public static final ResourceLocation SMITH_HAMMER = IsekaiMahou.id("smith_trigger_hammer");
    public static final ResourceLocation ROGUE_STEAL = IsekaiMahou.id("rogue_trigger_steal");
    public static final ResourceLocation KNIGHT_GUARD = IsekaiMahou.id("knight_trigger_guard");

    /** Par aumento/gatilho de cada classe + o contador de rank da classe e os ranks que liberam cada glifo. */
    public record ClassPair(MagicPath path, ResourceLocation augment, ResourceLocation trigger, String rankCounter,
                            int augmentRank, int triggerRank) {}

    /**
     * Ranks de cada classe (contadores gravados pelos módulos das classes). A escala muda por classe: Mago (teses 0..10),
     * Bruxa (revelações 0..5), Bruxo (pactos maiores 0..10), Druida (estágios 0..10), Necromante (ritos 0..7),
     * Ferreiro/Ladino/Cavaleiro (ranks 0..5). Os ranks batem com o tier em que os rituais das Mágicas ensinam glifos de origem (aumento = tier 3, gatilho = tier 4).
     */
    public static final List<ClassPair> PAIRS = List.of(
            new ClassPair(MagicPath.MAGE, MAGE_METAMAGIC, MAGE_WEAVE, "ritual_rank.mage", 3, 4),
            new ClassPair(MagicPath.WITCH, WITCH_INFUSION, WITCH_DRINK, "ritual_rank.witch", 3, 4),
            new ClassPair(MagicPath.WARLOCK, WARLOCK_PACT, WARLOCK_CURSED, "warlock.pacts", 5, 7),
            new ClassPair(MagicPath.DRUID, DRUID_OVERGROWTH, DRUID_ROOTED, "ritual_rank.druid", 5, 7),
            new ClassPair(MagicPath.NECROMANCER, NECRO_CHORUS, NECRO_MINION, "ritual_rank.necromancer", 4, 5),
            new ClassPair(MagicPath.SMITH, SMITH_EDGE, SMITH_HAMMER, "smith.rank", 2, 3),
            new ClassPair(MagicPath.ROGUE, ROGUE_BEHIND, ROGUE_STEAL, "ritual_rank.rogue", 2, 3),
            new ClassPair(MagicPath.KNIGHT, KNIGHT_CHALLENGE, KNIGHT_GUARD, "knight.oath", 2, 3));

    /** Todos os 16 ids (comando de teste). */
    public static List<ResourceLocation> all() {
        List<ResourceLocation> out = new ArrayList<>();
        for (ClassPair p : PAIRS) {
            out.add(p.augment());
            out.add(p.trigger());
        }
        return out;
    }

    // =====================================================================================
    //  Tipos
    // =====================================================================================

    /** Aumento de classe: marca o próprio id em {@link SpellStats#custom} (o comportamento é lido pelos eventos). */
    public static final class ClassAugment extends AugmentGlyph {
        private final MagicPath path;

        ClassAugment(ResourceLocation id, int cost, MagicPath path, int minCircle, Consumer<SpellStats> extra) {
            super(id.getPath(), cost, s -> {
                if (extra != null) extra.accept(s);
                s.custom.merge(id, 1, Integer::sum);
            });
            this.path = path;
            origin(path);
            tier(3);
            minCircle(minCircle);
        }

        @Override
        public int argb() { return path.argb(); }
    }

    /** Gatilho reativo de classe (vai no começo da receita). */
    public static final class ClassTrigger extends TriggerGlyph {
        private final MagicPath path;

        ClassTrigger(ResourceLocation id, int cost, MagicPath path, int minCircle) {
            super(id.getPath(), cost, true);
            this.path = path;
            origin(path);
            tier(4);
            minCircle(minCircle);
        }

        @Override
        public int argb() { return path.argb(); }
    }

    // =====================================================================================
    //  Registro
    // =====================================================================================
    public static void register() {
        // Metamagia: repete os aumentos que vieram ANTES dela no mesmo glifo (+40% de mana, +0,3 s de conjuração)
        GlyphRegistry.register(new ClassAugment(MAGE_METAMAGIC, 8, MagicPath.MAGE, 4, GcaGlyphs::metamagic).castTicks(6));
        GlyphRegistry.register(new ClassAugment(WITCH_INFUSION, 4, MagicPath.WITCH, 3, null));
        // Pacto de Sangue: a magia é paga com VIDA (como a Sobrecarga); o resto vem do GcaAugments
        GlyphRegistry.register(new ClassAugment(WARLOCK_PACT, 3, MagicPath.WARLOCK, 3, s -> s.overload = true));
        GlyphRegistry.register(new ClassAugment(DRUID_OVERGROWTH, 7, MagicPath.DRUID, 3, null));
        GlyphRegistry.register(new ClassAugment(NECRO_CHORUS, 8, MagicPath.NECROMANCER, 4, null));
        GlyphRegistry.register(new ClassAugment(SMITH_EDGE, 5, MagicPath.SMITH, 3, null));
        GlyphRegistry.register(new ClassAugment(ROGUE_BEHIND, 5, MagicPath.ROGUE, 3, null));
        GlyphRegistry.register(new ClassAugment(KNIGHT_CHALLENGE, 6, MagicPath.KNIGHT, 3, null));

        GlyphRegistry.register(new ClassTrigger(MAGE_WEAVE, 6, MagicPath.MAGE, 4));
        GlyphRegistry.register(new ClassTrigger(WITCH_DRINK, 5, MagicPath.WITCH, 4));
        GlyphRegistry.register(new ClassTrigger(WARLOCK_CURSED, 6, MagicPath.WARLOCK, 4));
        GlyphRegistry.register(new ClassTrigger(DRUID_ROOTED, 6, MagicPath.DRUID, 4));
        GlyphRegistry.register(new ClassTrigger(NECRO_MINION, 5, MagicPath.NECROMANCER, 4));
        GlyphRegistry.register(new ClassTrigger(SMITH_HAMMER, 5, MagicPath.SMITH, 4));
        GlyphRegistry.register(new ClassTrigger(ROGUE_STEAL, 5, MagicPath.ROGUE, 4));
        GlyphRegistry.register(new ClassTrigger(KNIGHT_GUARD, 6, MagicPath.KNIGHT, 4));
    }

    /**
     * Metamagia: soma de novo o que os aumentos anteriores deram a este glifo (cada campo ganha no máximo +2; Repetir
     * no máximo +1). Valores negativos (Enfraquecer, Encurtar...) e marcas sim/não ficam como estão. Não acumula.
     */
    private static void metamagic(SpellStats s) {
        if (s.custom(MAGE_METAMAGIC) > 0) return;
        s.amplify += twice(s.amplify);
        s.aoe += twice(s.aoe);
        s.duration += twice(s.duration);
        s.speed += twice(s.speed);
        s.split += twice(s.split);
        s.pierce += twice(s.pierce);
        s.ricochet += twice(s.ricochet);
        s.chain += twice(s.chain);
        s.charge += twice(s.charge);
        s.repeat += Math.min(1, Math.max(0, s.repeat));
    }

    private static int twice(int v) {
        return Math.min(2, Math.max(0, v));
    }

    // =====================================================================================
    //  Auxiliares
    // =====================================================================================

    /** O aumento está em algum glifo de algum estágio da magia? */
    public static boolean anywhere(ParsedSpell spell, ResourceLocation id) {
        return spell != null && spell.anyStats(s -> s.custom(id) > 0);
    }

    private GcaGlyphs() {}
}
