package com.renanpontes.isekaimahou.feature.forms_a.client;

import com.renanpontes.isekaimahou.feature.forms_a.FormsAEntities;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Parte de cliente do modulo "forms_a": renderers da Onda (só partículas), da Queda (núcleo brilhante)
 * e do raio do Feixe/Mira (fita de luz estilo farol).
 */
public class FormsAClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(FormsAClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(FormsAEntities.SPELL_WAVE.get(), NoopRenderer::new);
        e.registerEntityRenderer(FormsAEntities.SPELL_FALLING.get(), SpellFallingRenderer::new);
        e.registerEntityRenderer(FormsAEntities.SPELL_BEAM.get(), SpellBeamRenderer::new);
    }
}
