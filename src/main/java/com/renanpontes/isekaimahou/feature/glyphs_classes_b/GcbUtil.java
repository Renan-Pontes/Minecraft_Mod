package com.renanpontes.isekaimahou.feature.glyphs_classes_b;

import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.resources.ResourceLocation;

/** Leitura dos aumentos do módulo nos stats da magia. */
public final class GcbUtil {

    /** Maior número de cópias do aumento em qualquer glifo (forma ou efeito) de qualquer estágio; 0 = não tem. */
    public static int maxCustom(ParsedSpell spell, ResourceLocation id) {
        int best = 0;
        for (ParsedSpell s : spell.stages()) {
            best = Math.max(best, s.formStats().custom(id));
            for (ParsedSpell.EffectEntry e : s.effects()) best = Math.max(best, e.stats().custom(id));
        }
        return best;
    }

    /** Cópias do aumento neste glifo (os stats do dano) ou na forma do estágio. */
    public static int here(SpellStats stats, SpellStats formStats, ResourceLocation id) {
        return Math.max(stats == null ? 0 : stats.custom(id), formStats == null ? 0 : formStats.custom(id));
    }

    private GcbUtil() {}
}
