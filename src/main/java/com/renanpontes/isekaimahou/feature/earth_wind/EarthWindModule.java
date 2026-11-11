package com.renanpontes.isekaimahou.feature.earth_wind;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.SummonedStoneGolem;
import com.renanpontes.isekaimahou.feature.earth_wind.entity.SummonedSylph;
import com.renanpontes.isekaimahou.feature.earth_wind.glyph.EwGlyphs;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Modulo "earth_wind": glifos de efeito de TERRA e VENTO, com as invocacoes (Golem de Pedra, Silfide),
 * o Tornado, a Tempestade, blocos temporarios (pilares, muralhas, domo, areia movedica, fenda) e os
 * efeitos de status proprios (Pele de Pedra, Peso, Pulo Duplo, Planar, Velocidade do Vento,
 * Barreira de Vento, Passos Silenciosos).
 */
public class EarthWindModule implements Module {
    @Override
    public String id() {
        return "earth_wind";
    }

    @Override
    public void init(IEventBus modBus) {
        EwRegistries.register(modBus);
        modBus.addListener(EwNetwork::register);
        modBus.addListener(EarthWindModule::attributes);
        modBus.addListener(EarthWindModule::creativeTab);
        EwGlyphs.register();
        EwEvents.register(NeoForge.EVENT_BUS);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(EwRegistries.STONE_GOLEM_EGG.get());
            e.accept(EwRegistries.SYLPH_EGG.get());
        }
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(EwRegistries.STONE_GOLEM.get(), SummonedStoneGolem.createAttributes().build());
        e.put(EwRegistries.SYLPH.get(), SummonedSylph.createAttributes().build());
    }
}
