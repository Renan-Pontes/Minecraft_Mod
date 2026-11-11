package com.renanpontes.isekaimahou.feature.ritual_witch.client;

import com.renanpontes.isekaimahou.feature.ritual_witch.WitchContent;
import com.renanpontes.isekaimahou.feature.ritual_witch.WitchNetwork;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do modulo "ritual_witch": renderer do caldeirão, garrafa arremessada e tela do livro. */
public class RitualWitchClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(RitualWitchClient::renderers);
        // O pacote do livro chega no código comum; a tela é aberta aqui (só existe no cliente)
        WitchNetwork.openBook = data -> Minecraft.getInstance().setScreen(new WitchRecipeBookScreen(data));
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(WitchContent.CAULDRON_BE.get(), WitchCauldronRenderer::new);
        event.registerEntityRenderer(WitchContent.CURSE_BOTTLE.get(), ThrownItemRenderer::new);
    }
}
