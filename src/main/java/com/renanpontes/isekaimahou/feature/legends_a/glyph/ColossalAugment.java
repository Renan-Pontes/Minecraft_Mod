package com.renanpontes.isekaimahou.feature.legends_a.glyph;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.resources.ResourceLocation;

/**
 * Aumento "Colossal" (Arcano, tier 4): o glifo modificado fica com área ×2,5; a magia inteira fica com poder ×1,6,
 * custa ×2,5 de mana e leva +1 s (20 ticks) para conjurar. Não acumula (um basta). O custo e o poder extras são
 * aplicados por {@code LegendCasting} (SpellCastEvent); a área e o tempo vêm daqui.
 */
public class ColossalAugment extends AugmentGlyph {
    public static final ResourceLocation ID = IsekaiMahou.id("colossal");

    public ColossalAugment() {
        super("colossal", 10, ColossalAugment::modify);
        element(Element.ARCANE);
        tier(4);
        minCircle(3);
        castTicks(20);
    }

    private static void modify(SpellStats s) {
        // Área ×2,5: +3 de Expandir (1 + 0,5·3). Só na primeira vez no mesmo glifo.
        if (s.custom(ID) == 0) s.aoe += 3;
        s.custom.merge(ID, 1, Integer::sum);
    }
}
