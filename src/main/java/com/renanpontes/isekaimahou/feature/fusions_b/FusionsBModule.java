package com.renanpontes.isekaimahou.feature.fusions_b;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.fusions_b.glyph.AbyssGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_b.glyph.AstralGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_b.glyph.CrystalGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_b.glyph.GravityGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_b.glyph.HellfireGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_b.glyph.TwilightGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_b.glyph.VoidGlyphs;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "fusions_b": glifos de fusão de Cristal, Astral, Chama Infernal, Abismo, Gravidade, Vazio e Crepúsculo
 * (tiers 1–4 e o lendário Julgamento Final), com as entidades de magia, invocações e efeitos de status deles.
 */
public class FusionsBModule implements Module {
    @Override
    public String id() {
        return "fusions_b";
    }

    @Override
    public void init(IEventBus modBus) {
        FbRegistries.register(modBus);
        CrystalGlyphs.register();
        AstralGlyphs.register();
        HellfireGlyphs.register();
        AbyssGlyphs.register();
        GravityGlyphs.register();
        VoidGlyphs.register();
        TwilightGlyphs.register();
        FbEvents.register();

        // Diabrete Infernal: nasce do fogo do inferno (imune a fogo, sofre com luz e água)
        ElementReactions.setMultiplier(IsekaiMahou.id("hellfire_imp"), Element.FIRE, 0F);
        ElementReactions.setMultiplier(IsekaiMahou.id("hellfire_imp"), Element.LIGHT, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("hellfire_imp"), Element.WATER, 1.5F);
        // Clones do Crepúsculo: feitos de luz e sombra, resistem aos dois
        ElementReactions.setMultiplier(IsekaiMahou.id("twilight_clone"), Element.LIGHT, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("twilight_clone"), Element.SHADOW, 0.5F);
    }
}
