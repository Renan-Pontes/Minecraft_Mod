package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.LinkGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

/**
 * Análise do rascunho para o editor: resultado do {@link SpellParser}, qual(is) glifo(s) causam o erro (para
 * destacar no programa), elementos usados (selos) e o custo extra de multi-elemento. Imutável; a aba guarda a
 * última e só recalcula quando a receita ou os dados do jogador mudam.
 * <p>
 * v1.0: o elemento PRIMÁRIO vem dos glifos ({@link GrimoireDraft#primaryOf}) — não há mais elemento de destino.
 */
final class SpellCheck {
    /** Erros que o parser só emite DENTRO do laço (no glifo que falhou): achamos o culpado pelo menor prefixo. */
    private static final Set<String> LOOP_ERRORS = Set.of("unknown", "not_known", "circle", "wrong_element", "trigger_after_form",
            "trigger_not_placeable", "no_form_first", "effect_after_law", "augment_alone", "law_needs_domain", "link_first",
            "form_needs_link", "stage_form_not_allowed", "leap_not_last", "two_forms",
            // v1.4: Leis de Transformação
            "tlaw_needs_transform", "tlaw_duplicate", "tlaw_level", "tlaw_too_many");
    private static final String ERROR_PREFIX = "spell_error.isekai_mahou.";

    final SpellParser.Result result;
    /** Elemento primário (dos glifos; Arcano se não há glifo elemental). */
    final Element primary;
    /** Chave curta do erro (ex.: "form_needs_link"), null se válida. */
    @Nullable final String errorKey;
    /** Índices dos glifos culpados pelo erro (vazio = erro geral, ex.: "falta uma forma"). */
    final Set<Integer> culprits;
    /** Elementos da magia (primário + extras), na ordem do enum. */
    final Set<Element> elements;
    /** Elementos além do primário (cada um +10% de mana), na ordem em que aparecem. */
    final List<Element> extras;
    final int maxElements;
    final int maxGlyphs;
    final ProgramModel model;

    private SpellCheck(SpellParser.Result result, Element primary, @Nullable String errorKey, Set<Integer> culprits, Set<Element> elements,
                       List<Element> extras, int maxElements, int maxGlyphs, ProgramModel model) {
        this.result = result;
        this.primary = primary;
        this.errorKey = errorKey;
        this.culprits = culprits;
        this.elements = elements;
        this.extras = extras;
        this.maxElements = maxElements;
        this.maxGlyphs = maxGlyphs;
        this.model = model;
    }

    boolean ok() {
        return result.ok();
    }

    @Nullable
    ParsedSpell spell() {
        return result.spell();
    }

    /** Estágio {@code i} (0-based) da magia válida, ou null. */
    @Nullable
    ParsedSpell stage(int i) {
        return result.ok() ? result.spell().stage(i) : null;
    }

    /** Dica extra para o erro atual (tradução opcional "screen.isekai_mahou.grimoire.hint.<erro>"). */
    @Nullable
    Component hint() {
        if (errorKey == null) return null;
        String k = "screen.isekai_mahou.grimoire.hint." + errorKey;
        return Language.getInstance().has(k) ? Component.translatable(k) : null;
    }

    static SpellCheck of(List<ResourceLocation> glyphs, MagicData d) {
        SpellRecipe recipe = new SpellRecipe(glyphs, "", -1, "");
        SpellParser.Result res = SpellParser.parse(recipe, d);
        Element el = res.ok() ? res.spell().primary() : GrimoireDraft.primaryOf(glyphs);
        ProgramModel model = ProgramModel.build(glyphs);
        List<Element> extras = foreignOrder(glyphs, el);
        Set<Element> elements = EnumSet.noneOf(Element.class);
        if (res.ok() && !res.spell().elements().isEmpty()) {
            elements.addAll(res.spell().elements());
        } else {
            elements.add(el);
            elements.addAll(extras);
        }
        int maxEl = Progression.maxSpellElements(d.circle());
        int maxGl = Progression.maxGlyphs(d.level(), d.circle());
        String key = null;
        Set<Integer> culprits = Set.of();
        if (!res.ok()) {
            Object[] args = new Object[0];
            if (res.error().getContents() instanceof TranslatableContents tc) {
                String full = tc.getKey();
                key = full.startsWith(ERROR_PREFIX) ? full.substring(ERROR_PREFIX.length()) : full;
                args = tc.getArgs();
            }
            culprits = locate(glyphs, el, d, key, args, model, extras, maxEl, maxGl);
        }
        return new SpellCheck(res, el, key, culprits, elements, extras, maxEl, maxGl, model);
    }

    /** Elementos não-Arcanos que o primário não cobre sozinho, na ordem em que aparecem (mesma regra do parser). */
    static List<Element> foreignOrder(List<ResourceLocation> glyphs, Element el) {
        Set<Element> out = new LinkedHashSet<>();
        for (ResourceLocation id : glyphs) {
            Glyph g = GlyphRegistry.get(id);
            if (g == null || g.origin() != null || g.element() == Element.ARCANE) continue;
            if (el != null && el.canUseGlyphOf(g.element())) continue;
            out.add(g.element());
        }
        return new ArrayList<>(out);
    }

    /** Quantos elementos extras ainda cabem na magia (o primário não-Arcano ocupa uma vaga). */
    static int allowedExtras(Element el, int maxElements) {
        return Math.max(0, maxElements - (el != Element.ARCANE ? 1 : 0));
    }

    private static Set<Integer> locate(List<ResourceLocation> glyphs, Element el, MagicData d, @Nullable String key, Object[] args,
                                       ProgramModel model, List<Element> extras, int maxEl, int maxGl) {
        if (key == null) return Set.of();
        int n = glyphs.size();
        Set<Integer> out = new TreeSet<>();
        switch (key) {
            case "too_many_glyphs" -> {
                for (int i = Math.max(0, maxGl); i < n; i++) out.add(i);
            }
            case "too_many_elements" -> {
                int allowed = allowedExtras(el, maxEl);
                List<Element> over = extras.subList(Math.min(allowed, extras.size()), extras.size());
                for (int i = 0; i < n; i++) {
                    Glyph g = GlyphRegistry.get(glyphs.get(i));
                    if (g != null && g.origin() == null && over.contains(g.element())) out.add(i);
                }
            }
            case "element_not_awakened" -> {
                // o primário veio de glifos de um elemento ainda não despertado: todos eles são culpados
                for (int i = 0; i < n; i++) {
                    Glyph g = GlyphRegistry.get(glyphs.get(i));
                    if (g != null && g.origin() == null && g.element() == el) out.add(i);
                }
            }
            case "no_effect", "ultimate_circle" -> {
                ProgramModel.Stage st = model.stage(1);
                if (st != null && st.form >= 0) out.add(st.form);
            }
            case "stage_no_effect" -> {
                int number = args.length > 0 && args[0] instanceof Number num ? num.intValue() : -1;
                ProgramModel.Stage st = model.stage(number);
                if (st != null) {
                    if (st.form >= 0) out.add(st.form);
                    if (st.link >= 0) out.add(st.link);
                }
            }
            case "link_needs_form" -> {
                // o parser emite tanto no meio (veio efeito/aumento/lei/ligação depois da ligação) quanto no fim
                // (a receita acabou na ligação): a 1ª ligação que não é seguida de uma forma é a culpada
                for (ProgramModel.Stage st : model.stages) {
                    if (st.link < 0 || st.leap()) continue;
                    int next = st.link + 1;
                    Glyph g = next < n ? GlyphRegistry.get(glyphs.get(next)) : null;
                    if (g instanceof FormGlyph) continue;
                    out.add(st.link);
                    if (next < n) out.add(next);
                    break;
                }
            }
            default -> {
                if (!LOOP_ERRORS.contains(key)) break;
                // erros "de laço" dependem só dos glifos até o atual: o menor prefixo que já falha com o mesmo
                // erro termina exatamente no culpado
                for (int k = 1; k <= n; k++) {
                    SpellParser.Result r = SpellParser.parse(new SpellRecipe(glyphs.subList(0, k), "", -1, ""), d);
                    if (r.ok() || !(r.error().getContents() instanceof TranslatableContents tc)) continue;
                    if (!tc.getKey().equals(ERROR_PREFIX + key)) continue;
                    out.add(k - 1);
                    // Saltar numa forma que não aceita estágios: o parser acusa o Saltar, mas a forma também é culpada
                    if (key.equals("stage_form_not_allowed") && GlyphRegistry.get(glyphs.get(k - 1)) instanceof LinkGlyph) {
                        int s = model.stageOf(k - 1);
                        if (s >= 0 && model.stages.get(s).form >= 0) out.add(model.stages.get(s).form);
                    }
                    break;
                }
            }
        }
        return out;
    }
}
