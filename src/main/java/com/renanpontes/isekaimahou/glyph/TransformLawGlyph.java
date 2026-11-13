package com.renanpontes.isekaimahou.glyph;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * v1.4 — <b>Lei de Transformação</b> (um "voto" à moda de Jujutsu): uma regra que o jogador escolhe e que vale para ELE
 * MESMO enquanto está transformado, sempre com um BÔNUS e um PREÇO (ex.: Fúria Cega = +40% de dano corpo a corpo, mas
 * nada de magias de cura).
 * <p>
 * Continua sendo do tipo {@link GlyphKind#LAW} (todo código que separa leis continua funcionando e os ids das Leis de
 * Domínio não mudam): a diferença é o LUGAR onde vale. Leis de Domínio exigem uma forma com
 * {@link FormGlyph#acceptsLaws()}; Leis de Transformação exigem uma magia de TRANSFORMAÇÃO no estágio 0 — forma com
 * {@link FormGlyph#acceptsTransformLaws()} ou um efeito de transformação ({@link TransformTargets}) — e também podem ser
 * escolhidas para a Transformação Racial (tecla V). As regras em si são aplicadas pelo módulo {@code transform_laws}
 * enquanto a transformação durar ({@link TransformLaws#active}).
 * <p>
 * Tradução: além de {@code glyph.isekai_mahou.<id>} e {@code .desc}, cada lei tem {@code .boon} (o que ganha) e
 * {@code .price} (o que custa), mostrados em verde/vermelho no Grimório e na aba Votos.
 */
public class TransformLawGlyph extends LawGlyph {
    private int minLevel = 1;
    private int rgb = 0xFF5A8A;

    public TransformLawGlyph(String path, int cost) {
        super(path, cost);
    }

    /** Nível mínimo do jogador para usar a lei (ex.: Transcender só no nível 100). */
    public TransformLawGlyph minLevel(int level) {
        this.minLevel = Math.max(1, level);
        return this;
    }

    /** Cor de destaque da lei (partículas, faixa da aba Votos). */
    public TransformLawGlyph rgb(int rgb) {
        this.rgb = rgb & 0xFFFFFF;
        return this;
    }

    public int minLevel() { return minLevel; }

    public int rgbColor() { return rgb; }

    /** O que a lei dá (verde). */
    public MutableComponent boon() { return Component.translatable(translationKey() + ".boon"); }

    /** O que a lei cobra (vermelho). */
    public MutableComponent price() { return Component.translatable(translationKey() + ".price"); }

    /** O jogador pode usar a lei agora (Círculo e nível)? */
    public boolean usableAt(int level, int circle) {
        return level >= minLevel && circle >= minCircle();
    }
}
