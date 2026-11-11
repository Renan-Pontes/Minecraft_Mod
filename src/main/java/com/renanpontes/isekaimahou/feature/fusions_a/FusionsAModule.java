package com.renanpontes.isekaimahou.feature.fusions_a;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.fusions_a.glyph.IceGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_a.glyph.LightningGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_a.glyph.MagmaGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_a.glyph.NatureGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_a.glyph.PurityGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_a.glyph.SandGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_a.glyph.SteamGlyphs;
import com.renanpontes.isekaimahou.feature.fusions_a.glyph.SunGlyphs;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "fusions_a": glifos de fusão Magma, Vapor, Raio, Gelo, Natureza, Areia, Sol e Pureza; blocos
 * temporários (poça de lava, obsidiana, gelo, areia movediça, vidro), a zona das fusões, efeitos de status
 * próprios e o Ent invocado.
 */
public class FusionsAModule implements Module {
    @Override
    public String id() {
        return "fusions_a";
    }

    @Override
    public void init(IEventBus modBus) {
        FaRegistries.register(modBus);
        FaEvents.register();

        MagmaGlyphs.register();
        SteamGlyphs.register();
        LightningGlyphs.register();
        IceGlyphs.register();
        NatureGlyphs.register();
        SandGlyphs.register();
        SunGlyphs.register();
        PurityGlyphs.register();

        // O Ent é madeira viva: o fogo o castiga, água e terra quase não o ferem
        ElementReactions.setMultiplier(IsekaiMahou.id("summoned_ent"), Element.FIRE, 2F);
        ElementReactions.setMultiplier(IsekaiMahou.id("summoned_ent"), Element.WATER, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("summoned_ent"), Element.EARTH, 0.5F);
    }
}
