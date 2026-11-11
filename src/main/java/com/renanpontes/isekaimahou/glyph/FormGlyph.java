package com.renanpontes.isekaimahou.glyph;

/**
 * Forma: decide COMO a magia sai e QUEM/O QUE ela atinge.
 * Implementações chamam {@link SpellContext#resolveOnEntity}, {@link SpellContext#resolveOnBlock},
 * {@link SpellContext#resolveHit} ou {@link SpellContext#resolveArea} para aplicar os efeitos.
 * Use {@code ctx.formStats()} para ler os aumentos aplicados na forma (velocidade, divisão, área...).
 */
public abstract class FormGlyph extends Glyph {

    protected FormGlyph(String path, int cost) {
        super(path, GlyphKind.FORM, cost);
    }

    /** Executa a forma (servidor). Chamado depois de pagar mana e terminar a conjuração. */
    public abstract void cast(SpellContext ctx);

    /** Ticks de conjuração base (segurar a tecla). 0 = instantânea. */
    public int baseCastTicks() { return 0; }

    /** Recarga base em ticks (somada a um valor proporcional ao custo). */
    public int baseCooldown() { return 10; }

    /** Ultimates (Cataclismo, Expansão de Domínio): só no slot G e gastam a Carga de Ultimate. */
    public boolean isUltimate() { return false; }

    /** Pode receber glifos de Lei depois dela (Domínios). */
    public boolean acceptsLaws() { return false; }

    /** Esta forma cria magia que fica no mundo (zona, torre...), para limitar quantidade por jogador. */
    public boolean isPersistent() { return false; }

    /** Forma que funciona sem efeito nenhum (ex.: Domínio Simples, que é só defesa). Padrão: exige ≥1 efeito. */
    public boolean requiresEffect() { return true; }

    /**
     * v0.7: a forma pode ser usada como estágio 2+ de uma magia programável (depois de Ao Acertar etc.) e
     * pode terminar em Saltar. Só retorne true se a forma: (1) sai de {@code ctx.eyePos()}/{@code ctx.look()}
     * (ou de {@code ctx.anchor()}, se for de área) em vez de ler o conjurador direto, e (2) chama
     * {@code ctx.impact(...)} nos impactos reais e {@code ctx.end(...)} quando termina.
     * Formas presas ao conjurador (Si Mesmo, Aura, Domínio...) ficam false.
     */
    public boolean stageable() { return false; }

    // =====================================================================================
    //  v1.0 — custo por cópia e detalhamento
    // =====================================================================================

    /**
     * Quantas CÓPIAS dos efeitos esta forma aplica numa conjuração (projéteis do Dividir, gotas da Chuva, meteoros da
     * Chuva de Meteoros, pulsos de um Domínio...). O {@link SpellParser} multiplica o custo dos efeitos (e dos aumentos
     * deles) por este número, já suavizado por {@link SpellCost#moderate}. Padrão: o contador registrado em
     * {@link SpellCost#registerCopies} para o id da forma; sem registro, {@code 1 + Dividir} ({@link SpellStats#count()}).
     * Formas novas com várias cópias DEVEM sobrescrever (ex.: Saraivada = número de flechas).
     *
     * @param stats  aumentos aplicados na forma
     * @param circle Círculo de quem conjura (mobs/sem dados: {@link SpellCost#MOB_CIRCLE})
     */
    public float copies(SpellStats stats, int circle) {
        return SpellCost.defaultCopies(this, stats, circle);
    }

    /** Alcance aproximado em blocos (detalhamento). &lt; 0 = desconhecido. Padrão: {@link GlyphHints}. */
    public float hintRange() { return GlyphHints.getOrNone(id()).range(); }

    /** Raio base em blocos (antes de Expandir/Concentrar). &lt; 0 = a forma não tem área. Padrão: {@link GlyphHints}. */
    public float hintRadius() { return GlyphHints.getOrNone(id()).radius(); }

    /** Duração base em ticks (antes de Estender/Persistente). &lt; 0 = instantânea. Padrão: {@link GlyphHints}. */
    public int hintDuration() { return GlyphHints.getOrNone(id()).duration(); }
}
