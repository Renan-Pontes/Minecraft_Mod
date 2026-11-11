package com.renanpontes.isekaimahou.glyph;

/**
 * v0.7 — Ligação entre ESTÁGIOS de uma magia programável (ex.: Feixe → Ao Acertar → Zona).
 * Continua sendo do tipo {@link GlyphKind#TRIGGER} (para não quebrar quem faz switch em GlyphKind), mas
 * {@link #isLink()} é true e ela pode aparecer DEPOIS da forma: fecha o estágio atual e abre o próximo.
 */
public class LinkGlyph extends TriggerGlyph {
    /** Quando o próximo estágio dispara. */
    public enum LinkKind {
        /** No ponto de impacto (entidade ou bloco). */
        HIT,
        /** Onde o estágio atual terminou (projétil expirou, zona acabou, feixe chegou ao fim). */
        END,
        /** Onde um alvo morreu por dano deste estágio. */
        KILL,
        /** Terminal: repete o estágio atual a partir do alvo atingido em direção ao próximo inimigo (paga mana por salto). */
        LEAP
    }

    private final LinkKind linkKind;

    public LinkGlyph(String path, int cost, LinkKind linkKind) {
        super(path, cost, true);
        this.linkKind = linkKind;
    }

    public LinkKind linkKind() { return linkKind; }

    @Override
    public boolean isLink() { return true; }
}
