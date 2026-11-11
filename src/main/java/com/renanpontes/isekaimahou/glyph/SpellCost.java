package com.renanpontes.isekaimahou.glyph;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.Progression;

import net.minecraft.resources.ResourceLocation;

/**
 * v1.0 — Regras de CUSTO das magias (o {@link SpellParser} usa) e do elemento primário vindo dos glifos.
 *
 * <h3>Custo por cópia</h3>
 * Para cada estágio da magia:
 * <pre>
 * instâncias  = cópias(forma) × (1 + Repetir/Eco da forma) × (1 + Encadear da forma)
 * custo_efeito = (custo do efeito + aumentos dele) × (1 + Repetir do efeito) × (1 + Encadear do efeito)   (mín. 1)
 * custo_estágio = [ (forma + aumentos da forma) + Σ custo_efeito × instâncias + ligações ] × disparos do estágio
 * </pre>
 * {@code cópias(forma)} vem de {@link FormGlyph#copies} (padrão: {@code 1 + Dividir}; Chuva = gotas, Meteoros = meteoros;
 * ULTIMATES e Domínios contam "de forma moderada": Chuva de Meteoros = 1 + √meteoros, Domínios = 1 + √pulsos —
 * {@link #pulseCopies}), suavizado por {@link #moderate}
 * (até {@value #MODERATE_FROM} cópias contam inteiras; depois cada cópia conta {@value #MODERATE_RATE}; teto
 * {@value #MAX_COPIES}). {@code disparos do estágio} = quantas vezes o estágio anterior pode dispará-lo
 * ({@link #stageFires}: {@code 1 + Dividir} por disparo do anterior, o mesmo limite do {@code SpellContext}; no máximo
 * {@value #MAX_STAGE_TRIGGERS}). Gatilhos e leis entram uma vez. Saltar continua pagando POR SALTO na hora
 * ({@link SpellContext#hopCost()}), então não entra na conta.
 * <p>
 * Depois da soma: +10% por elemento extra ({@link SpellParser#EXTRA_ELEMENT_COST}), Eficiente ×0,75, Instantâneo ×1,5 e
 * a inflação de números da v1.0 {@link #COST_SCALE} (×3, igual à mana — a razão custo/mana de uma magia simples não muda).
 * O custo "antigo" (soma simples dos glifos, sem escala) continua em {@link ParsedSpell#baseCost()} e é o que define
 * tempo de conjuração, recarga, XP, carga de ultimate e anúncio (balanço da v0.8 preservado).
 */
public final class SpellCost {
    /** Inflação v1.0: custos ×3 (a mana base também é ×3 — ver {@code core.classes.ResourceTraining}). */
    public static final float COST_SCALE = 3.0F;
    /** Círculo assumido nas contas de cópias quando não há dados do jogador (mobs, pedestais, lendas). */
    public static final int MOB_CIRCLE = 5;
    /** Até quantas cópias contam inteiras. */
    public static final int MODERATE_FROM = 6;
    /** Quanto conta cada cópia além de {@link #MODERATE_FROM}. */
    public static final float MODERATE_RATE = 0.5F;
    /** Teto de cópias contadas (depois da suavização). */
    public static final float MAX_COPIES = 40.0F;
    /** Teto de disparos de um estágio ligado (Ao Acertar/Ao Terminar/Ao Abater). */
    public static final int MAX_STAGE_TRIGGERS = 8;

    /** Conta as cópias de uma forma a partir dos aumentos dela e do Círculo de quem conjura. */
    @FunctionalInterface
    public interface CopyCounter {
        float copies(SpellStats stats, int circle);
    }

    private static final Map<ResourceLocation, CopyCounter> COPIES = new ConcurrentHashMap<>();

    static {
        // Formas dos módulos com várias cópias (por id: o núcleo não depende das classes deles).
        // Os números espelham o que cada forma realmente cria no cast().
        registerCopies("rain", (s, c) -> 8 * (1 + Math.max(0, s.aoe)) + 4 * Math.max(0, s.split));
        registerCopies("meteor", (s, c) -> clamp(s.count(), 1, 6));
        // Ultimate: os meteoros caem ao longo da duração como pulsos — conta 1 + √meteoros (Círculo 10: 50 → ≈8,1)
        registerCopies("meteor_shower", (s, c) -> 1F + (float) Math.sqrt(clamp(10 + 4 * c, 10, 60)));
        registerCopies("sky_lances", (s, c) -> clamp(3 + Math.max(0, s.split), 3, 8));
        registerCopies("arsenal_gate", (s, c) -> Math.min(12, 4 + 2 * Math.max(0, s.split)));
        // Domínios: pulsos por segundo × duração, de forma moderada (ver pulseCopies)
        registerCopies("domain_expansion", (s, c) -> pulseCopies(s.duration(250), 20));
        registerCopies("simple_domain", (s, c) -> pulseCopies(s.duration(100), 20));
        // Convergir cria no máximo 3 pontos
        registerCopies("converge", (s, c) -> Math.min(3, s.count()));
        // Formas que IGNORAM o Dividir (área única, presas ao conjurador, ultimates de um golpe): 1 cópia — um Dividir
        // nelas não multiplica os efeitos, então também não multiplica o custo (só aumenta os disparos de estágio)
        for (String single : new String[] {"touch", "self", "stance", "cone", "nova", "aura", "zone", "pulse", "wall",
                "seal", "portal", "turret", "spiral_sphere", "time_stop", "super_tier", "colossus", "transcend", "cataclysm"}) {
            registerCopies(single, (s, c) -> 1F);
        }
    }

    /** Registra o contador de cópias de uma forma (id completo). Formas podem, em vez disso, sobrescrever {@link FormGlyph#copies}. */
    public static void registerCopies(ResourceLocation formId, CopyCounter counter) {
        if (formId != null && counter != null) COPIES.put(formId, counter);
    }

    /** Atalho para ids do mod ({@code isekai_mahou:<path>}). */
    public static void registerCopies(String path, CopyCounter counter) {
        registerCopies(IsekaiMahou.id(path), counter);
    }

    /** Padrão de {@link FormGlyph#copies}: contador registrado, ou {@code 1 + Dividir}. */
    public static float defaultCopies(FormGlyph form, SpellStats stats, int circle) {
        CopyCounter c = COPIES.get(form.id());
        return c != null ? c.copies(stats, circle) : stats.count();
    }

    /** Cópias de uma forma JÁ suavizadas ({@link #moderate}), nunca menos que 1. */
    public static float formCopies(FormGlyph form, SpellStats stats, int circle) {
        float raw;
        try {
            raw = form.copies(stats, circle);
        } catch (RuntimeException e) {
            // Uma forma de módulo com conta quebrada não pode impedir o parse da magia
            IsekaiMahou.LOGGER.warn("Forma {} falhou ao contar copias: {}", form.id(), e.toString());
            raw = stats.count();
        }
        return moderate(raw);
    }

    /**
     * Suavização das cópias: as primeiras {@link #MODERATE_FROM} contam inteiras, as seguintes {@link #MODERATE_RATE}
     * cada; nunca menos que 1 nem mais que {@link #MAX_COPIES}.
     */
    public static float moderate(float raw) {
        if (Float.isNaN(raw) || raw <= 1F) return 1F;
        float n = raw <= MODERATE_FROM ? raw : MODERATE_FROM + (raw - MODERATE_FROM) * MODERATE_RATE;
        return Math.min(MAX_COPIES, n);
    }

    /**
     * Cópias de uma forma que pulsa (Domínios, ultimates de área): {@code 1 + √(pulsos)} — 10 s pulsando 1×/s contam
     * ≈ 4,2 cópias (não 10), para o Domínio continuar viável com efeitos fortes.
     *
     * @param durationTicks duração da forma (já com Estender/Persistente)
     * @param intervalTicks ticks entre pulsos
     */
    public static float pulseCopies(int durationTicks, int intervalTicks) {
        float pulses = Math.max(0, durationTicks) / (float) Math.max(1, intervalTicks);
        return 1F + (float) Math.sqrt(pulses);
    }

    /**
     * Quantas vezes UM disparo de um estágio pode disparar o próximo (Ao Acertar/Ao Terminar/Ao Abater/Saltar):
     * {@code 1 + Dividir} — o mesmo limite que o {@code SpellContext} aplica ({@code state.fires}). Repetir/Eco
     * reaproveitam o mesmo contador, então não multiplicam.
     */
    public static int stageFires(SpellStats formStats) {
        return Math.max(1, formStats.count());
    }

    /** Multiplicador de dano de magia da v1.0 ({@code Progression.DAMAGE_SCALE}, ×1,5). */
    public static float damageScale() {
        return Progression.DAMAGE_SCALE;
    }

    // =====================================================================================
    //  Elemento primário (v1.0: vem dos glifos, não do slot)
    // =====================================================================================

    /**
     * Elemento primário de uma receita: entre os elementos não-Arcanos dos glifos, o que COBRE mais os outros (uma
     * fusão cobre os pais); empate = o do primeiro EFEITO elemental (depois, o do primeiro glifo elemental).
     * Receita só com glifos Arcanos = {@link Element#ARCANE}.
     * <p>
     * Glifos de ORIGEM (de classe) são usáveis em magia de qualquer elemento: só definem o primário quando a receita não
     * tem nenhum outro glifo elemental (aí a magia "é" do elemento da classe, sem exigir que ele esteja despertado —
     * ver {@link #primaryFromOrigin}).
     */
    public static Element primaryElement(@Nullable SpellRecipe recipe) {
        if (recipe == null || recipe.isEmpty()) return Element.ARCANE;
        List<Glyph> glyphs = new ArrayList<>(recipe.glyphs().size());
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null) glyphs.add(g);
        }
        return primaryElement(glyphs);
    }

    /** Igual a {@link #primaryElement(SpellRecipe)}, a partir dos glifos já resolvidos. */
    public static Element primaryElement(List<? extends Glyph> glyphs) {
        boolean origin = primaryFromOrigin(glyphs);
        List<Element> order = new ArrayList<>();
        // Efeitos primeiro (o "primeiro efeito elemental" desempata), depois os demais glifos
        for (Glyph g : glyphs) {
            if (g == null || (g.origin() != null) != origin) continue;
            if (g.kind() == GlyphKind.EFFECT && g.element() != Element.ARCANE && !order.contains(g.element())) order.add(g.element());
        }
        for (Glyph g : glyphs) {
            if (g == null || (g.origin() != null) != origin) continue;
            if (g.element() != Element.ARCANE && !order.contains(g.element())) order.add(g.element());
        }
        if (order.isEmpty()) return Element.ARCANE;
        Element best = order.get(0);
        int bestCover = -1;
        for (Element c : order) {
            int cover = 0;
            for (Element o : order) if (c.canUseGlyphOf(o)) cover++;
            if (cover > bestCover) { bestCover = cover; best = c; }
        }
        return best;
    }

    /**
     * O primário vem só de glifos de ORIGEM? (nenhum glifo comum tem elemento não-Arcano, mas algum de origem tem).
     * Nesse caso o parser não exige o elemento despertado (glifos de origem valem em qualquer magia).
     */
    public static boolean primaryFromOrigin(List<? extends Glyph> glyphs) {
        boolean originElement = false;
        for (Glyph g : glyphs) {
            if (g == null || g.element() == Element.ARCANE) continue;
            if (g.origin() == null) return false;
            originElement = true;
        }
        return originElement;
    }

    private static float clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private SpellCost() {}
}
