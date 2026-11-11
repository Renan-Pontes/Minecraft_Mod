package com.renanpontes.isekaimahou.glyph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;

/**
 * Magia já validada e pronta para executar.
 *
 * v0.7: uma magia pode ter vários ESTÁGIOS ligados por {@link LinkGlyph} (ex.: Feixe → Ao Acertar → Zona).
 * O record raiz é o estágio 0 — todos os campos antigos ({@link #form()}, {@link #effects()}, {@link #formStats()},
 * {@link #laws()}...) continuam descrevendo o estágio 0, então código antigo funciona igual.
 * Estágios seguintes ficam em {@link #next()}; neles {@code manaCost/castTicks/cooldown} são 0 (a raiz guarda os totais).
 *
 * @param triggers    gatilhos condicionais/sequência no começo da receita (só na raiz)
 * @param form        a forma deste estágio
 * @param formStats   aumentos aplicados na forma
 * @param effects     efeitos, cada um com seus aumentos
 * @param laws        leis (só no estágio 0, em formas que aceitam leis)
 * @param manaCost    custo final de mana (raiz: total da magia inteira; estágios-filho: 0)
 * @param castTicks   ticks de conjuração (raiz; filhos: 0)
 * @param cooldown    recarga em ticks (raiz; filhos: 0)
 * @param glyphCount  quantos glifos (raiz: a receita inteira; filhos: só os do estágio)
 * @param next        próximo estágio (null = último). Com {@code link_leap}, {@code next.spell()} é null: veja {@link #leap()}
 * @param elements    elementos usados pela magia inteira (o primário incluso); o mesmo conjunto em todos os estágios
 * @param primary     v1.0: elemento primário da magia, vindo dos glifos ({@link SpellCost#primaryElement}); o mesmo em
 *                    todos os estágios
 * @param baseCost    v1.0: custo "antigo" (soma simples dos glifos com os modificadores da v0.8, SEM custo por cópia e
 *                    SEM a escala ×3) — usado para tempo de conjuração, recarga, XP, carga de ultimate, anúncio e
 *                    patamares (Pilha, integridade do Domínio). Raiz: total; estágios-filho: 0
 * @param stageCost   v1.0: contas de custo DESTE estágio (custo por cópia) — ver {@link StageCost}
 */
public record ParsedSpell(List<TriggerGlyph> triggers, FormGlyph form, SpellStats formStats, List<EffectEntry> effects,
                          List<LawGlyph> laws, int manaCost, int castTicks, int cooldown, int glyphCount,
                          @Nullable Stage next, Set<Element> elements, Element primary, int baseCost, StageCost stageCost) {

    public record EffectEntry(EffectGlyph effect, SpellStats stats) {}

    /**
     * v1.0 — Contas de custo de UM estágio (valores crus, antes de {@code scale}; ver {@link SpellCost}).
     *
     * @param formCost    forma + aumentos aplicados nela (pago uma vez por disparo do estágio)
     * @param effectCosts custo de cada efeito + aumentos dele, POR CÓPIA (mesma ordem de {@link #effects()})
     * @param effectMults multiplicador próprio de cada efeito: (1 + Repetir do efeito) × (1 + Encadear do efeito)
     * @param copies      cópias da forma, já suavizadas ({@link SpellCost#formCopies})
     * @param instances   cópias × (1 + Repetir da forma) × (1 + Encadear da forma)
     * @param fixedCost   gatilhos, ligação que sai do estágio e leis (pagos uma vez por disparo)
     * @param triggers    quantas vezes o estágio pode ser disparado pelo anterior (estágio 0 = 1)
     * @param scale       multiplicador final da magia (elementos extras × Eficiente × Instantâneo × {@link SpellCost#COST_SCALE})
     * @param total       custo final deste estágio em mana (com tudo aplicado)
     */
    public record StageCost(int formCost, List<Integer> effectCosts, List<Float> effectMults, float copies, float instances,
                            int fixedCost, float triggers, float scale, int total) {
        public StageCost {
            effectCosts = List.copyOf(effectCosts);
            effectMults = List.copyOf(effectMults);
        }

        /** Custo sem detalhamento (construtores de compatibilidade). */
        public static StageCost flat(int cost) {
            return new StageCost(Math.max(0, cost), List.of(), List.of(), 1F, 1F, 0, 1F, 1F, Math.max(0, cost));
        }

        /** Custo cru de UM efeito somando todas as cópias dele neste estágio (sem {@code scale} nem disparos). */
        public float effectTotalRaw(int index) {
            if (index < 0 || index >= effectCosts.size()) return 0F;
            float mult = index < effectMults.size() ? effectMults.get(index) : 1F;
            return effectCosts.get(index) * mult * instances;
        }
    }

    /**
     * Ligação para o próximo estágio.
     * @param link  o glifo de ligação (Ao Acertar, Ao Terminar, Ao Abater, Saltar)
     * @param spell o próximo estágio; null quando o link é {@code link_leap} (que repete o estágio atual)
     */
    public record Stage(LinkGlyph link, @Nullable ParsedSpell spell) {}

    public ParsedSpell {
        elements = elements == null || elements.isEmpty() ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(elements));
        if (primary == null) primary = elements.isEmpty() ? Element.ARCANE : elements.iterator().next();
        if (stageCost == null) stageCost = StageCost.flat(manaCost);
    }

    /** Compatibilidade v0.7: sem custo por cópia (custo base = custo de mana; primário = primeiro elemento). */
    public ParsedSpell(List<TriggerGlyph> triggers, FormGlyph form, SpellStats formStats, List<EffectEntry> effects,
                       List<LawGlyph> laws, int manaCost, int castTicks, int cooldown, int glyphCount,
                       @Nullable Stage next, Set<Element> elements) {
        this(triggers, form, formStats, effects, laws, manaCost, castTicks, cooldown, glyphCount, next, elements,
                null, manaCost, null);
    }

    /** Compatibilidade v0.6: magia de um estágio só, sem informação de elementos. */
    public ParsedSpell(List<TriggerGlyph> triggers, FormGlyph form, SpellStats formStats, List<EffectEntry> effects,
                       List<LawGlyph> laws, int manaCost, int castTicks, int cooldown, int glyphCount) {
        this(triggers, form, formStats, effects, laws, manaCost, castTicks, cooldown, glyphCount, null, Set.of());
    }

    /**
     * v1.0 — Detalhamento da magia para o Grimório: por estágio e por efeito, custo de cada parte, cópias, dano/cura
     * estimados (base × poder × {@code DAMAGE_SCALE}), duração, raio e alcance; e os totais (mana, conjuração,
     * recarga). Chame na RAIZ (estágio 0) para ver a magia inteira.
     */
    public SpellBreakdown breakdown() {
        return SpellBreakdown.of(this);
    }

    /** v1.0: quantas cópias dos efeitos este estágio aplica (já suavizado; ver {@link SpellCost}). */
    public float instances() {
        return stageCost.instances();
    }

    public boolean isUltimate() {
        return form.isUltimate();
    }

    public boolean hasLaw(String path) {
        return laws.stream().anyMatch(l -> l.id().getPath().equals(path));
    }

    public boolean hasTrigger(String path) {
        return triggers.stream().anyMatch(t -> t.id().getPath().equals(path));
    }

    /** v0.8: a magia tem o aumento Instantâneo em algum glifo (conjura na hora, fora da Pilha). */
    public boolean isInstant() {
        return anyStats(SpellStats::instant);
    }

    // =====================================================================================
    //  Estágios (v0.7)
    // =====================================================================================

    /** Existe um próximo estágio de verdade (Ao Acertar/Ao Terminar/Ao Abater)? {@code link_leap} não conta. */
    public boolean hasNext() {
        return next != null && next.spell() != null;
    }

    /** Este estágio termina em {@code link_leap} (Saltar): repete-se de alvo em alvo. */
    public boolean leap() {
        return next != null && next.link().linkKind() == LinkGlyph.LinkKind.LEAP;
    }

    /** Tipo da ligação que sai deste estágio, ou null se ele é o último sem Saltar. */
    @Nullable
    public LinkGlyph.LinkKind linkKind() {
        return next == null ? null : next.link().linkKind();
    }

    /** Quantos estágios a partir deste (inclusive). Uma magia sem ligações tem 1. */
    public int stageCount() {
        return 1 + (hasNext() ? next.spell().stageCount() : 0);
    }

    /** Estágio {@code i} contando a partir deste (0 = este), ou null se não existe. */
    @Nullable
    public ParsedSpell stage(int i) {
        if (i < 0) return null;
        if (i == 0) return this;
        return hasNext() ? next.spell().stage(i - 1) : null;
    }

    /** Todos os estágios a partir deste, em ordem (útil para desenhar o "programa" no Grimório). */
    public List<ParsedSpell> stages() {
        List<ParsedSpell> out = new ArrayList<>();
        for (ParsedSpell s = this; s != null; s = s.hasNext() ? s.next().spell() : null) out.add(s);
        return out;
    }

    /** Algum aumento (da forma ou de um efeito) em QUALQUER estágio a partir deste satisfaz o teste? */
    public boolean anyStats(Predicate<SpellStats> test) {
        for (ParsedSpell s : stages()) {
            if (test.test(s.formStats())) return true;
            for (EffectEntry e : s.effects()) if (test.test(e.stats())) return true;
        }
        return false;
    }
}
