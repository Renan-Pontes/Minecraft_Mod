package com.renanpontes.isekaimahou.glyph;

/**
 * Gatilho.
 * <ul>
 *   <li><b>De movimento</b> (placeable=false): agachado/correndo/no ar/na água. Não entram na receita:
 *       saber o glifo libera a versão correspondente no slot (ver {@link MoveTrigger}). Registrados no núcleo.</li>
 *   <li><b>Condicional/sequência</b> (placeable=true): vão no começo da receita (Ao Receber Dano, Depois, Junto...).
 *       O módulo Arcano implementa o comportamento ouvindo {@code SpellCastEvent} e eventos do jogo.</li>
 * </ul>
 */
public class TriggerGlyph extends Glyph {
    private final boolean placeable;

    public TriggerGlyph(String path, int cost, boolean placeable) {
        super(path, GlyphKind.TRIGGER, cost);
        this.placeable = placeable;
    }

    public boolean isPlaceable() { return placeable; }

    /** v0.7: glifos de ligação entre estágios ({@link LinkGlyph}) retornam true. */
    public boolean isLink() { return false; }
}
