package com.renanpontes.isekaimahou.feature.legends_j.client;

import com.renanpontes.isekaimahou.feature.legends_j.LjRegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Parte de cliente do "legends_j": só o renderer da rocha do Tengai Shinsei. O resto do visual (raios, correntes,
 * faíscas, cortes, clarões) sai do núcleo de efeitos ({@code ElementFx}), que já respeita o Preset Gráfico.
 */
public class LegendsJClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(LegendsJClient::renderers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(LjRegistry.METEOR_ROCK.get(), MeteorRockRenderer::new);
    }
}
