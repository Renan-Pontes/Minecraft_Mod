package com.renanpontes.isekaimahou.feature.arcane.client;

import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "arcane": contorno dos minérios do glifo Detectar.
 * (Os blocos de luz/pedra conjurada usam "render_type" no próprio modelo JSON; não precisam de registro aqui.)
 */
public class ArcaneClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(OreRevealRenderer::onRender);
        NeoForge.EVENT_BUS.addListener(OreRevealRenderer::onLogout);
    }
}
