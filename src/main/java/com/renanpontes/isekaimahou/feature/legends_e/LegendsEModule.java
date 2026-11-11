package com.renanpontes.isekaimahou.feature.legends_e;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.legends_e.glyph.BlackGoatEffect;
import com.renanpontes.isekaimahou.feature.legends_e.glyph.DeathClockEffect;
import com.renanpontes.isekaimahou.feature.legends_e.glyph.FallenDownEffect;
import com.renanpontes.isekaimahou.feature.legends_e.glyph.GraspHeartEffect;
import com.renanpontes.isekaimahou.feature.legends_e.glyph.SuperTierForm;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Módulo "legends_e" (v0.8): Magias de Nível Supremo (homenagem a Overlord).
 * Glifos: forma ultimate {@code super_tier}; efeitos {@code fallen_down}, {@code black_goat}, {@code grasp_heart},
 * {@code death_clock}. Item {@code arcane_hourglass}; entidade {@code dark_young}.
 * Lendas: Queda Final, Iä Shub-Niggurath, Agarrar o Coração, O Fim de Toda Vida é a Morte, Queda de Meteoros Suprema.
 */
public class LegendsEModule implements Module {
    @Override
    public String id() {
        return "legends_e";
    }

    @Override
    public void init(IEventBus modBus) {
        LeRegistry.register(modBus);
        modBus.addListener(LeNet::register);
        modBus.addListener(LegendRecipes::validate);

        GlyphRegistry.register(new SuperTierForm());
        GlyphRegistry.register(new FallenDownEffect());
        GlyphRegistry.register(new BlackGoatEffect());
        GlyphRegistry.register(new GraspHeartEffect());
        GlyphRegistry.register(new DeathClockEffect());

        LegendRecipes.register();
        SupremeCasting.init();

        // Filhote Sombrio: cria da escuridão — a luz o fere, a sombra quase não
        ElementReactions.setMultiplier(IsekaiMahou.id("dark_young"), Element.LIGHT, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("dark_young"), Element.SHADOW, 0.25F);

        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            LeUtil.clear();
            SupremeCasting.clear();
        });
    }
}
