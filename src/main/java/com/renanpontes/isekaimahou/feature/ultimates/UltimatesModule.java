package com.renanpontes.isekaimahou.feature.ultimates;

import com.renanpontes.isekaimahou.feature.ultimates.glyph.ColossusForm;
import com.renanpontes.isekaimahou.feature.ultimates.glyph.MeteorForm;
import com.renanpontes.isekaimahou.feature.ultimates.glyph.MeteorShowerForm;
import com.renanpontes.isekaimahou.feature.ultimates.glyph.TranscendForm;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "ultimates" (v0.8, §8): Meteoro e novas magias de G.
 * <ul>
 *   <li>forma {@code meteor} (estágio) — rocha em brasa cai do céu no ponto mirado, cratera e onda de choque;</li>
 *   <li>ultimate {@code meteor_shower} — céu escarlate e dezenas de meteoros por 15 s;</li>
 *   <li>ultimate {@code transcend} — transformação de 30 s com voo, auréola e asas, bônus e pulsos;</li>
 *   <li>ultimate {@code colossus} — gigante espectral de ~12 blocos que corta, pisa e protege o dono.</li>
 * </ul>
 * Entidades {@code meteor} e {@code spectral_colossus} (GeckoLib, não salvas); efeito {@code transcendence}.
 * Lendas: Meteoro, Chuva de Meteoros, Despertar, Colosso Espectral.
 */
public class UltimatesModule implements Module {
    @Override
    public String id() {
        return "ultimates";
    }

    @Override
    public void init(IEventBus modBus) {
        UltRegistries.register(modBus);
        modBus.addListener(UltNet::register);
        modBus.addListener(UltLegends::validate);

        GlyphRegistry.register(new MeteorForm());
        GlyphRegistry.register(new MeteorShowerForm());
        GlyphRegistry.register(new TranscendForm());
        GlyphRegistry.register(new ColossusForm());

        UltLegends.register();
        Transcendence.init();
    }
}
