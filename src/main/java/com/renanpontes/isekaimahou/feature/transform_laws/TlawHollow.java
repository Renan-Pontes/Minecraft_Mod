package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.List;

import com.renanpontes.isekaimahou.feature.class_hollow.HollowRelease;
import com.renanpontes.isekaimahou.feature.class_hollow.glyph.HollowGlyphs;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.TransformLawGlyph;
import com.renanpontes.isekaimahou.glyph.TransformTargets;

/**
 * Integração com o Hollow (v1.4, pedido do módulo class_hollow):
 * <ul>
 *   <li>a <b>Resurrección</b> (forma liberada do Arrancar) usa as leis escolhidas na aba Votos (seção "Resurrección",
 *       só para Hollows): começa em {@code HollowRelease.ON_START} pelo tempo que falta, acaba em {@code ON_END} (com os
 *       preços de fim só quando acabou "de verdade");</li>
 *   <li>o glifo antigo {@code hollowfied_resurreccion} aceita leis na receita (registrado como transformação);</li>
 *   <li>os 10 glifos antigos do Hollow ({@code HollowGlyphs.LEGACY}) ficam APOSENTADOS ({@link GlyphRegistry#hide}):
 *       continuam registrados, mas somem das listas do Grimório.</li>
 * </ul>
 */
final class TlawHollow {
    static void init() {
        for (String id : HollowGlyphs.LEGACY) GlyphRegistry.hide(id);
        TransformTargets.registerForm("hollowfied_resurreccion", null, 1200);
        HollowRelease.ON_START.add((sp, release) -> {
            // Release 1.0: mesmo sem votos soltos, a receita do espaço "hollow_resurreccion" entra por cima
            List<TransformLawGlyph> laws = TlawRace.usablePicks(sp, TlawRace.HOLLOW);
            int ticks = Math.max(40, HollowRelease.ticksLeft(sp)) + 40; // o ON_END é quem encerra
            TlawSessions.INSTANCE.begin(sp, TlawSessions.HOLLOW_SOURCE, laws, ticks, null);
        });
        HollowRelease.ON_END.add((sp, release, natural) -> {
            if (natural) TlawSessions.INSTANCE.end(sp, TlawSessions.HOLLOW_SOURCE);
            else TlawSessions.drop(sp, TlawSessions.HOLLOW_SOURCE);
        });
    }

    private TlawHollow() {}
}
