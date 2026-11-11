package com.renanpontes.isekaimahou.feature.origins_a;

import com.renanpontes.isekaimahou.feature.origins_a.familiar.Familiars;
import com.renanpontes.isekaimahou.feature.origins_a.glyph.CurseGlyphs;
import com.renanpontes.isekaimahou.feature.origins_a.glyph.RuneGlyphs;
import com.renanpontes.isekaimahou.feature.origins_a.glyph.ShapeGlyphs;
import com.renanpontes.isekaimahou.feature.origins_a.shape.ShapeEvents;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Modulo "origins_a": Magias de Origem.
 * <ul>
 *   <li>Runas (Mago): Selo Explosivo, Círculo de Teleporte, Runa de Retorno, Ward, Encantamento Temporário;</li>
 *   <li>Maldições (Bruxa): Hex, Boneco de Vodu, Mau-Olhado, Virar Sapo, Familiar (Corvo/Gato);</li>
 *   <li>Metamorfose (Druida): Lobo, Urso, Falcão, Golfinho, Aranha, Fera Elemental.</li>
 * </ul>
 * Todos são efeitos ARCANOS com origem (usáveis em magia de qualquer elemento) e tier 1..5 — os rituais
 * de classe ensinam conforme o tier.
 */
public class OriginsAModule implements Module {
    @Override
    public String id() {
        return "origins_a";
    }

    @Override
    public void init(IEventBus modBus) {
        OaRegistries.register(modBus);
        modBus.addListener(OaNetwork::register);

        RuneGlyphs.register();
        CurseGlyphs.register();
        ShapeGlyphs.register();

        OaEvents.register();
        ShapeEvents.register();
        Familiars.register();
    }
}
