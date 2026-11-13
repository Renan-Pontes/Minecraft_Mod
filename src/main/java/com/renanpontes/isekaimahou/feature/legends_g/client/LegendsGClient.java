package com.renanpontes.isekaimahou.feature.legends_g.client;

import com.renanpontes.isekaimahou.feature.legends_g.LgRegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Parte de cliente do módulo "legends_g": só os renderers do Grim e da bala do Canhão de Pedra. O resto do visual
 * (feixes, cortes, anéis, auras, clarões) é montado pelo núcleo de efeitos ({@code ElementFx}), que já respeita o
 * Preset Gráfico ({@code FxQuality}) de cada jogador.
 */
public class LegendsGClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(LegendsGClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(LgRegistry.GRIMOIRE.get(), GrimoireRenderer::new);
        e.registerEntityRenderer(LgRegistry.STONE_SHELL.get(), StoneShellRenderer::new);
    }
}
