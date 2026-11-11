package com.renanpontes.isekaimahou.glyph;

import java.util.function.Consumer;

/**
 * Aumento: modifica o glifo anterior (forma ou efeito) mexendo nos {@link SpellStats}.
 * Todos os aumentos do plano são registrados no núcleo ({@code CoreGlyphs}); módulos podem criar
 * aumentos próprios usando {@link SpellStats#custom}.
 */
public class AugmentGlyph extends Glyph {
    private final Consumer<SpellStats> modifier;
    private int castTicksBonus;

    public AugmentGlyph(String path, int cost, Consumer<SpellStats> modifier) {
        super(path, GlyphKind.AUGMENT, cost);
        this.modifier = modifier;
    }

    /** Ticks extras de conjuração ao usar este aumento (ex.: Carregar). */
    public AugmentGlyph castTicks(int ticks) {
        this.castTicksBonus = ticks;
        return this;
    }

    public int castTicksBonus() { return castTicksBonus; }

    public void apply(SpellStats stats) {
        modifier.accept(stats);
    }
}
