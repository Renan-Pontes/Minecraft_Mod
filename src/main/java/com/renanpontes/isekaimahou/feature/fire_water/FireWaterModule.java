package com.renanpontes.isekaimahou.feature.fire_water;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "fire_water": glifos de efeito de Fogo e Água (tiers 1–5), campos elementais (Fumaça, Névoa,
 * Chuva Local, Dilúvio, Muralha de Fogo), efeitos de status próprios e as invocações Salamandra e Ondina.
 */
public class FireWaterModule implements Module {
    @Override
    public String id() {
        return "fire_water";
    }

    @Override
    public void init(IEventBus modBus) {
        FwRegistries.register(modBus);
        FireGlyphs.register();
        WaterGlyphs.register();
        FwEvents.register();

        // Resistências/fraquezas das invocações
        ElementReactions.setMultiplier(IsekaiMahou.id("summoned_salamander"), Element.FIRE, 0F);
        ElementReactions.setMultiplier(IsekaiMahou.id("summoned_salamander"), Element.WATER, 2F);
        ElementReactions.setMultiplier(IsekaiMahou.id("summoned_undine"), Element.WATER, 0F);
        ElementReactions.setMultiplier(IsekaiMahou.id("summoned_undine"), Element.FIRE, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("summoned_undine"), Element.EARTH, 1.5F);
    }
}
