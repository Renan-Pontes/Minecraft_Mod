package com.renanpontes.isekaimahou.feature.forms_b.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.forms_b.summon.SummonForge;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.resources.ResourceLocation;

/**
 * Glifos de ORDEM da Forja de Invocações (aumentos neutros, liberados junto com a forma Invocação: tier 4, Círculo 3).
 * Vão logo depois da Invocação (ou do glifo do corpo) e dizem como a criatura se comporta desde que nasce:
 * Guardar o ponto onde surgiu, Caçar o alvo que você mirava, ou Ficar parada. Sem glifo de ordem: Seguir, como sempre.
 * A ordem pode ser trocada depois pela tecla de ordem. Em outras formas estes aumentos não fazem nada.
 */
public final class OrderGlyphs {
    private OrderGlyphs() {}

    public static void register() {
        add(SummonForge.GLYPH_GUARD);
        add(SummonForge.GLYPH_HUNT);
        add(SummonForge.GLYPH_HOLD);
    }

    private static void add(ResourceLocation id) {
        AugmentGlyph g = new AugmentGlyph(id.getPath(), 2, s -> s.custom.merge(id, 1, Integer::sum));
        g.element(Element.ARCANE);
        g.tier(4);
        g.minCircle(3);
        GlyphRegistry.register(g);
    }
}
