package com.renanpontes.isekaimahou.glyph;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Valida uma {@link SpellRecipe} e transforma em {@link ParsedSpell}.
 * Usado no servidor (antes de conjurar) e no cliente (editor do Grimório mostra erros/custo).
 *
 * Gramática v0.7 (magias programáveis):
 * <pre>
 * [TRIGGER...] STAGE (LINK STAGE)* [LINK_LEAP] [LAW...]
 * STAGE = FORM [AUGMENT...] (EFFECT [AUGMENT...])*
 * </pre>
 * Uma receita sem ligações continua sendo exatamente a gramática antiga.
 * <p>
 * v1.0: o elemento primário vem dos glifos ({@link SpellCost#primaryElement}) e precisa estar despertado; o custo de
 * mana é o CUSTO POR CÓPIA de {@link SpellCost} (fórmula documentada lá) × a escala ×3; o custo antigo continua em
 * {@link ParsedSpell#baseCost()} (tempo de conjuração e recarga saem dele, como na v0.8).
 */
public final class SpellParser {

    /** Custo extra por elemento além do primário (+10% cada). */
    public static final float EXTRA_ELEMENT_COST = 0.10F;

    /** Resultado do parse: ou a magia, ou uma mensagem de erro. */
    public record Result(ParsedSpell spell, Component error) {
        public boolean ok() { return spell != null; }
        static Result fail(String key, Object... args) {
            return new Result(null, Component.translatable("spell_error.isekai_mahou." + key, args));
        }
    }

    /**
     * Regra de ELEMENTO (multi-elemento v0.7): uma magia cujo slot é {@code slotElement} pode usar o glifo?
     * Sim se o glifo é Arcano, de Origem, do próprio elemento, de um pai da fusão, ou de qualquer elemento que o
     * jogador já despertou. Não checa conhecimento nem Círculo (o parser faz isso à parte).
     *
     * @param slotElement elemento primário (do slot); null = sem regra de elemento (mobs)
     * @param data        dados do jogador; null = mob/criativo: não exige despertar
     */
    public static boolean canUse(@Nullable Element slotElement, Glyph g, @Nullable MagicData data) {
        if (slotElement == null || g.origin() != null) return true;
        if (slotElement.canUseGlyphOf(g.element())) return true;
        return data == null || data.isAwakened(g.element());
    }

    /** Estágio em montagem durante o parse. */
    private static final class StageDraft {
        FormGlyph form;
        final SpellStats formStats = new SpellStats();
        final List<ParsedSpell.EffectEntry> effects = new ArrayList<>();
        int glyphs;
        /** Ligação que fecha este estágio (null = último estágio sem Saltar). */
        LinkGlyph linkOut;
        // ---- v1.0: custo por cópia (valores crus) ----
        /** Forma + aumentos aplicados nela. */
        int formCost;
        /** Custo de cada efeito + aumentos dele (mesma ordem de {@link #effects}). */
        final List<Integer> effectCosts = new ArrayList<>();
        /** Gatilhos, ligação de saída e leis. */
        int fixedCost;
    }

    /**
     * v1.0: conjuração de JOGADOR — o elemento primário vem dos glifos ({@link SpellCost#primaryElement}).
     * @param data dados do jogador (null = criativo: não checa conhecimento/Círculo/limites)
     */
    public static Result parse(SpellRecipe recipe, @Nullable MagicData data) {
        return parse(recipe, Element.ARCANE, data);
    }

    /**
     * @param element   modo da regra de elemento: {@code null} = MOB (sem regra de elemento nem acréscimo por elemento
     *                  extra); qualquer outro valor = regra de JOGADOR. v1.0: o elemento primário NÃO vem mais deste
     *                  parâmetro (nem do slot): sempre dos glifos ({@link SpellCost#primaryElement}) — o valor passado
     *                  só escolhe o modo. Veja {@link ParsedSpell#primary()}.
     * @param data      dados do jogador para checar glifos conhecidos/Círculo/limites (null = mob/criativo: não checa)
     */
    public static Result parse(SpellRecipe recipe, Element element, MagicData data) {
        if (recipe == null || recipe.isEmpty()) return Result.fail("empty");
        // Resolve os glifos antes: o primário depende de TODOS eles
        List<Glyph> resolved = new ArrayList<>(recipe.glyphs().size());
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g == null) return Result.fail("unknown", id.toString());
            resolved.add(g);
        }
        Element primary = SpellCost.primaryElement(resolved);
        if (element != null) {
            element = primary;
            // Primário vindo só de glifos de origem (classe): vale sem despertar, como os glifos de origem sempre valeram
            if (data != null && !data.isAwakened(primary) && !SpellCost.primaryFromOrigin(resolved)) {
                return Result.fail("element_not_awakened", primary.displayName());
            }
        }

        List<TriggerGlyph> triggers = new ArrayList<>();
        List<LawGlyph> laws = new ArrayList<>();
        List<StageDraft> stages = new ArrayList<>();
        StageDraft cur = new StageDraft();
        stages.add(cur);
        boolean leapClosed = false;
        SpellStats current = null; // stats que recebem os próximos aumentos
        int cost = 0;
        int extraCastTicks = 0;
        // Elementos não-Arcanos que o primário NÃO cobre sozinho (cada um encarece a magia)
        Set<Element> foreign = EnumSet.noneOf(Element.class);

        // Aumentos vão para: -1 = a forma do estágio atual; k = o efeito k do estágio atual
        int costTarget = -1;
        for (Glyph g : resolved) {
            ResourceLocation id = g.id();

            if (data != null) {
                if (!data.knowsGlyph(id)) return Result.fail("not_known", g.displayName());
                if (data.circle() < g.minCircle()) return Result.fail("circle", g.displayName(), g.minCircle());
            }
            if (!canUse(element, g, data)) {
                return Result.fail("wrong_element", g.displayName(), element.displayName());
            }
            if (g.origin() == null && g.element() != Element.ARCANE
                    && (element == null || !element.canUseGlyphOf(g.element()))) {
                foreign.add(g.element());
            }

            // Estágio aberto por uma ligação que ainda não recebeu a forma
            boolean awaitingForm = stages.size() > 1 && cur.form == null;
            switch (g.kind()) {
                case TRIGGER -> {
                    TriggerGlyph t = (TriggerGlyph) g;
                    if (t instanceof LinkGlyph link) {
                        if (leapClosed) return Result.fail("leap_not_last");
                        if (!laws.isEmpty()) return Result.fail("effect_after_law");
                        if (cur.form == null) {
                            return stages.size() == 1 ? Result.fail("link_first", g.displayName())
                                    : Result.fail("link_needs_form", stages.get(stages.size() - 2).linkOut.displayName());
                        }
                        if (cur.effects.isEmpty()) return Result.fail("stage_no_effect", stages.size());
                        cur.linkOut = link;
                        cur.glyphs++;
                        cur.fixedCost += g.cost();
                        if (link.linkKind() == LinkGlyph.LinkKind.LEAP) {
                            // Saltar repete o estágio a partir do alvo: a forma precisa respeitar a origem
                            if (!cur.form.stageable()) return Result.fail("stage_form_not_allowed", cur.form.displayName());
                            leapClosed = true;
                        } else {
                            cur = new StageDraft();
                            stages.add(cur);
                        }
                        current = null;
                    } else {
                        if (cur.form != null || stages.size() > 1) return Result.fail("trigger_after_form", g.displayName());
                        if (!t.isPlaceable()) return Result.fail("trigger_not_placeable", g.displayName());
                        triggers.add(t);
                        cur.fixedCost += g.cost();
                    }
                }
                case FORM -> {
                    if (leapClosed) return Result.fail("leap_not_last");
                    if (!laws.isEmpty()) return Result.fail("effect_after_law");
                    FormGlyph f = (FormGlyph) g;
                    // v0.7: outra forma é permitida, mas só depois de uma ligação (que abre o próximo estágio)
                    if (cur.form != null) return Result.fail("form_needs_link", f.displayName());
                    if (stages.size() > 1 && (!f.stageable() || f.isUltimate() || f.acceptsLaws())) {
                        return Result.fail("stage_form_not_allowed", f.displayName());
                    }
                    cur.form = f;
                    cur.glyphs++;
                    cur.formCost += g.cost();
                    current = cur.formStats;
                    costTarget = -1;
                }
                case EFFECT -> {
                    if (leapClosed) return Result.fail("leap_not_last");
                    if (awaitingForm) return Result.fail("link_needs_form", stages.get(stages.size() - 2).linkOut.displayName());
                    if (cur.form == null) return Result.fail("no_form_first");
                    if (!laws.isEmpty()) return Result.fail("effect_after_law");
                    SpellStats s = new SpellStats();
                    cur.effects.add(new ParsedSpell.EffectEntry((EffectGlyph) g, s));
                    cur.effectCosts.add(g.cost());
                    cur.glyphs++;
                    current = s;
                    costTarget = cur.effects.size() - 1;
                }
                case AUGMENT -> {
                    if (leapClosed) return Result.fail("leap_not_last");
                    if (awaitingForm) return Result.fail("link_needs_form", stages.get(stages.size() - 2).linkOut.displayName());
                    if (current == null) return Result.fail("augment_alone", g.displayName());
                    AugmentGlyph a = (AugmentGlyph) g;
                    a.apply(current);
                    extraCastTicks += a.castTicksBonus();
                    cur.glyphs++;
                    if (costTarget < 0) cur.formCost += g.cost();
                    else cur.effectCosts.set(costTarget, cur.effectCosts.get(costTarget) + g.cost());
                }
                case LAW -> {
                    // Leis só no FIM da receita (depois do último estágio/Saltar) e só se a forma do estágio 0
                    // aceita leis: elas valem para o estágio 0 (o Domínio), mesmo com estágios ligados depois
                    if (awaitingForm) return Result.fail("link_needs_form", stages.get(stages.size() - 2).linkOut.displayName());
                    if (stages.get(0).form == null || !stages.get(0).form.acceptsLaws()) {
                        return Result.fail("law_needs_domain", g.displayName());
                    }
                    laws.add((LawGlyph) g);
                    stages.get(0).fixedCost += g.cost();
                    current = null;
                }
            }
            cost += g.cost();
        }

        StageDraft first = stages.get(0);
        if (first.form == null) return Result.fail("no_form");
        if (cur.form == null) return Result.fail("link_needs_form", stages.get(stages.size() - 2).linkOut.displayName());
        if (first.effects.isEmpty() && !first.form.acceptsLaws() && first.form.requiresEffect()) return Result.fail("no_effect");
        for (int i = 1; i < stages.size(); i++) {
            if (stages.get(i).effects.isEmpty()) return Result.fail("stage_no_effect", i + 1);
        }

        int count = recipe.glyphs().size();
        if (data != null) {
            int max = Progression.maxGlyphs(data.level(), data.circle());
            if (count > max) return Result.fail("too_many_glyphs", count, max);
        }
        if (data != null && first.form.isUltimate() && !Progression.ultimatesAllowed(data.circle())) {
            return Result.fail("ultimate_circle", 4);
        }

        // Elementos da magia (primário incluso)
        Set<Element> elements = EnumSet.noneOf(Element.class);
        if (element != null) elements.add(element);
        elements.addAll(foreign);
        if (elements.isEmpty()) elements.add(Element.ARCANE);
        if (data != null) {
            int distinct = (int) elements.stream().filter(e -> e != Element.ARCANE).count();
            int max = Progression.maxSpellElements(data.circle());
            if (distinct > max) return Result.fail("too_many_elements", distinct, max);
        }

        // Custo final (soma de TODOS os estágios e ligações)
        boolean efficient = false;
        for (StageDraft d : stages) {
            if (d.formStats.efficient || d.effects.stream().anyMatch(e -> e.stats().efficient)) efficient = true;
        }
        cost = Math.max(1, cost);
        // Mobs (element null) não pagam mana: sem acréscimo, para o custo deles continuar igual ao da v0.6
        int extras = element != null ? foreign.size() : 0;
        float mult = 1.0F;
        if (extras > 0) {
            cost = Math.round(cost * (1.0F + EXTRA_ELEMENT_COST * extras));
            mult *= 1.0F + EXTRA_ELEMENT_COST * extras;
        }
        if (efficient) {
            cost = Math.round(cost * 0.75F);
            mult *= 0.75F;
        }
        // v0.8 — Instantâneo: +50% de custo e conjuração na hora (nunca entra na Pilha)
        boolean instant = false;
        for (StageDraft d : stages) {
            if (d.formStats.instant() || d.effects.stream().anyMatch(e -> e.stats().instant())) instant = true;
        }
        if (instant) {
            cost = Math.round(cost * 1.5F);
            mult *= 1.5F;
        }
        // v1.0 — custo por cópia (ver SpellCost) e a escala ×3 da inflação de números
        float scale = mult * SpellCost.COST_SCALE;
        int circle = data != null ? data.circle() : SpellCost.MOB_CIRCLE;
        List<ParsedSpell.StageCost> stageCosts = new ArrayList<>(stages.size());
        float triggered = 1F;
        float rawTotal = 0F;
        for (StageDraft d : stages) {
            float copies = SpellCost.formCopies(d.form, d.formStats, circle);
            float instances = copies * (1 + Math.max(0, d.formStats.repeat)) * (1 + Math.max(0, d.formStats.chain));
            List<Float> effectMults = new ArrayList<>(d.effects.size());
            List<Integer> effectCosts = new ArrayList<>(d.effects.size());
            float effectsRaw = 0F;
            for (int i = 0; i < d.effects.size(); i++) {
                SpellStats es = d.effects.get(i).stats();
                float em = (1 + Math.max(0, es.repeat)) * (1 + Math.max(0, es.chain));
                int ec = Math.max(1, d.effectCosts.get(i));
                effectMults.add(em);
                effectCosts.add(ec);
                effectsRaw += ec * em;
            }
            int formCost = Math.max(0, d.formCost);
            int fixed = Math.max(0, d.fixedCost);
            float stageRaw = (formCost + effectsRaw * instances + fixed) * triggered;
            rawTotal += stageRaw;
            stageCosts.add(new ParsedSpell.StageCost(formCost, effectCosts, effectMults, copies, instances,
                    fixed, triggered, scale, Math.round(stageRaw * scale)));
            // O próximo estágio dispara no máximo (1 + Dividir) vezes por disparo deste — é o limite real do
            // SpellContext.fireNext (as gotas da Chuva, por exemplo, NÃO disparam um estágio cada) — com teto
            triggered = Math.min(SpellCost.MAX_STAGE_TRIGGERS, triggered * SpellCost.stageFires(d.formStats));
        }
        int manaCost = Math.max(1, Math.round(rawTotal * scale));

        int castTicks = first.form.baseCastTicks() + extraCastTicks + Math.max(0, (cost - 80) / 4);
        if (efficient) castTicks = Math.round(castTicks * 1.5F) + 5;
        if (instant) castTicks = 0;
        int cooldown = first.form.baseCooldown() + cost / 3;

        // Monta de trás para frente: cada estágio aponta para o seguinte
        ParsedSpell built = null;
        for (int i = stages.size() - 1; i >= 0; i--) {
            StageDraft d = stages.get(i);
            ParsedSpell.Stage next = d.linkOut == null ? null : new ParsedSpell.Stage(d.linkOut, built);
            built = i == 0
                    ? new ParsedSpell(List.copyOf(triggers), d.form, d.formStats, List.copyOf(d.effects), List.copyOf(laws),
                            manaCost, castTicks, cooldown, count, next, elements, primary, cost, stageCosts.get(i))
                    : new ParsedSpell(List.of(), d.form, d.formStats, List.copyOf(d.effects), List.of(),
                            0, 0, 0, d.glyphs, next, elements, primary, 0, stageCosts.get(i));
        }
        return new Result(built, null);
    }

    private SpellParser() {}
}
