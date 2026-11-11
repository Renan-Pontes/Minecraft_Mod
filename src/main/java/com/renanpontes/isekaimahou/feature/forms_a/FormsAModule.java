package com.renanpontes.isekaimahou.feature.forms_a;

import com.renanpontes.isekaimahou.feature.forms_a.glyph.BeamForm;
import com.renanpontes.isekaimahou.feature.forms_a.glyph.ConeForm;
import com.renanpontes.isekaimahou.feature.forms_a.glyph.FallForm;
import com.renanpontes.isekaimahou.feature.forms_a.glyph.MarkForm;
import com.renanpontes.isekaimahou.feature.forms_a.glyph.NovaForm;
import com.renanpontes.isekaimahou.feature.forms_a.glyph.RainForm;
import com.renanpontes.isekaimahou.feature.forms_a.glyph.SlashForm;
import com.renanpontes.isekaimahou.feature.forms_a.glyph.WaveForm;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Modulo "forms_a": formas neutras (arcanas) Feixe, Cone, Corte, Nova, Onda, Queda, Chuva e Mira,
 * mais as entidades da Onda ({@code spell_wave}) e da Queda ({@code spell_falling}).
 */
public class FormsAModule implements Module {
    @Override
    public String id() {
        return "forms_a";
    }

    @Override
    public void init(IEventBus modBus) {
        FormsAEntities.register(modBus);

        GlyphRegistry.register(new BeamForm());
        GlyphRegistry.register(new ConeForm());
        GlyphRegistry.register(new SlashForm());
        GlyphRegistry.register(new NovaForm());
        GlyphRegistry.register(new WaveForm());
        GlyphRegistry.register(new FallForm());
        GlyphRegistry.register(new RainForm());
        GlyphRegistry.register(new MarkForm());
    }
}
