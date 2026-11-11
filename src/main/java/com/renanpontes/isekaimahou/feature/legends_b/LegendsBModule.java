package com.renanpontes.isekaimahou.feature.legends_b;

import com.renanpontes.isekaimahou.feature.legends_b.entity.SpiralOrbEntity;
import com.renanpontes.isekaimahou.feature.legends_b.glyph.AnnihilateEffect;
import com.renanpontes.isekaimahou.feature.legends_b.glyph.ConvergeForm;
import com.renanpontes.isekaimahou.feature.legends_b.glyph.SkyLancesForm;
import com.renanpontes.isekaimahou.feature.legends_b.glyph.SlashStormEffect;
import com.renanpontes.isekaimahou.feature.legends_b.glyph.SpiralSphereForm;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Módulo "legends_b": Magias Lendárias B (v0.7).
 * Glifos: forma Convergência ({@code converge}), efeito Aniquilação ({@code annihilate}), forma Lanças do Céu
 * ({@code sky_lances}), forma Esfera Espiral ({@code spiral_sphere}), efeito Tempestade de Cortes ({@code slash_storm}).
 * Lendas: Vazio Roxo, Ruína Carmesim, Santuário Maligno, Megido Solar, Esfera Espiral, Raio Assassino, Lei Sagrada.
 */
public class LegendsBModule implements Module {
    @Override
    public String id() {
        return "legends_b";
    }

    @Override
    public void init(IEventBus modBus) {
        LegendsBRegistry.register(modBus);
        modBus.addListener(LegendsBNet::register);
        modBus.addListener(LegendRecipes::validate);

        GlyphRegistry.register(new ConvergeForm());
        GlyphRegistry.register(new SkyLancesForm());
        GlyphRegistry.register(new SpiralSphereForm());
        GlyphRegistry.register(new AnnihilateEffect());
        GlyphRegistry.register(new SlashStormEffect());

        LegendRecipes.register();

        NeoForge.EVENT_BUS.addListener(SpiralOrbEntity::onAttack);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            SlashStormEffect.resetMarks();
            SpiralOrbEntity.clearHeld();
        });
    }
}
