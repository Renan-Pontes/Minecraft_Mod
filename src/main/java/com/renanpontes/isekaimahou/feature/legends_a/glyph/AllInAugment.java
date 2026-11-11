package com.renanpontes.isekaimahou.feature.legends_a.glyph;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.resources.ResourceLocation;

/**
 * Aumento "Tudo ou Nada" (Arcano, tier 4): a magia gasta TODO o mana que sobrar. O poder vira
 * ×(1 + mana extra / custo), até ×4; o glifo modificado ganha +50% de área. Depois o conjurador desmaia:
 * Exaustão Mágica (sem conjurar e sem regenerar mana), lentidão e fraqueza por 6 s.
 * O efeito de mana/poder/desmaio é aplicado por {@code LegendCasting} (SpellCastEvent).
 */
public class AllInAugment extends AugmentGlyph {
    public static final ResourceLocation ID = IsekaiMahou.id("all_in");

    public AllInAugment() {
        super("all_in", 10, AllInAugment::modify);
        element(Element.ARCANE);
        tier(4);
        minCircle(3);
    }

    private static void modify(SpellStats s) {
        // Não acumula: dois "Tudo ou Nada" no mesmo glifo não gastam o mana duas vezes
        if (s.custom(ID) == 0) s.aoe += 1;
        s.custom.merge(ID, 1, Integer::sum);
    }
}
