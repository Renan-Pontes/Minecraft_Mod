package com.renanpontes.isekaimahou.feature.legends_c.law;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.LawGlyph;

/**
 * As duas Leis de Domínio das Técnicas Malditas. São {@link LawGlyph} comuns (vão no fim da receita, depois de uma
 * Expansão de Domínio); o efeito de cada uma é um {@link Domains.LawHandler} registrado no núcleo — o módulo domain
 * chama os handlers enquanto o domínio está aberto.
 */
public final class LcLaws {
    public static final String SHRINE = "law_shrine";
    public static final String INFINITE_VOID = "law_infinite_void";

    /** Lei: Santuário Maligno. */
    public static final class ShrineLawGlyph extends LawGlyph {
        public ShrineLawGlyph() {
            super("law_shrine", 40);
            element(Element.ARCANE);
            tier(5);
            minCircle(6);
        }
    }

    /** Lei: Vazio Infinito. */
    public static final class VoidLawGlyph extends LawGlyph {
        public VoidLawGlyph() {
            super("law_infinite_void", 40);
            element(Element.ARCANE);
            tier(5);
            minCircle(6);
        }
    }

    public static void register() {
        GlyphRegistry.register(new ShrineLawGlyph());
        GlyphRegistry.register(new VoidLawGlyph());
        Domains.registerLaw(IsekaiMahou.id(SHRINE), new ShrineLaw());
        Domains.registerLaw(IsekaiMahou.id(INFINITE_VOID), new VoidLaw());
    }

    private LcLaws() {}
}
