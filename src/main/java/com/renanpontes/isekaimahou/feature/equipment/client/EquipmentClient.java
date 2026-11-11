package com.renanpontes.isekaimahou.feature.equipment.client;

import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "equipment": camada de animação de conjuração do Player Animator
 * (próprio jogador via ClientHooks.castState; outros via pacote) e a aparência dos acessórios no corpo.
 */
public class EquipmentClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        CastAnimations.init();
        // Antes do AddLayers, quando o Curios instancia os renderers registrados
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> CurioRenderers.register());
        // Tecla G do Curios x tecla G da magia suprema
        NeoForge.EVENT_BUS.addListener(CuriosKeyFix::onLogin);
    }
}
