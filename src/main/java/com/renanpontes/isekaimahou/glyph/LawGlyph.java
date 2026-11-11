package com.renanpontes.isekaimahou.glyph;

/**
 * Lei de Domínio (Acerto Garantido, Sem Fuga, Território...). Só vale depois de uma forma com
 * {@link FormGlyph#acceptsLaws()}. O módulo de Domínio define as leis (subclasses) e lê
 * {@code ctx.spell().laws()} para aplicar as regras.
 */
public class LawGlyph extends Glyph {

    public LawGlyph(String path, int cost) {
        super(path, GlyphKind.LAW, cost);
    }
}
