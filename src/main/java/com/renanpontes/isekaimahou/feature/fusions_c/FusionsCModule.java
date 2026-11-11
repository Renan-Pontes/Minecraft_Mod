package com.renanpontes.isekaimahou.feature.fusions_c;

import com.renanpontes.isekaimahou.feature.fusions_c.glyph.FcIceGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_c.glyph.FcLightningGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_c.glyph.FcMagmaGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_c.glyph.FcNatureGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_c.glyph.FcPurityGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_c.glyph.FcSandGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_c.glyph.FcSteamGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_c.glyph.FcSunGlyphs;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "fusions_c" (v0.8, §9): MISTURAS de Magma, Vapor, Raio, Gelo, Natureza, Areia, Sol e Pureza — 16 glifos
 * tier 3–4 com visual próprio (Gêiser de Lava, Vulcão, Explosão/Canhão de Vapor, Canhão Elétrico, Bola de Raio, Zero
 * Absoluto, Caixão de Gelo, Raízes do Mundo, Tempestade de Pólen, Caixão/Tsunami de Areia, Supernova, Erupção Solar,
 * Fonte Sagrada, Purgar) e 3 Magias Lendárias. Destruição de terreno sempre pela API Destruction do núcleo.
 */
public class FusionsCModule implements Module {
    @Override
    public String id() {
        return "fusions_c";
    }

    @Override
    public void init(IEventBus modBus) {
        FcRegistries.register(modBus);
        modBus.addListener(FcNet::register);
        modBus.addListener(FcLegends::validate);
        FcEvents.register();

        FcMagmaGlyphs.register();
        FcSteamGlyphs.register();
        FcLightningGlyphs.register();
        FcIceGlyphs.register();
        FcNatureGlyphs.register();
        FcSandGlyphs.register();
        FcSunGlyphs.register();
        FcPurityGlyphs.register();

        FcLegends.register();
    }
}
