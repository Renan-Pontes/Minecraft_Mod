package com.renanpontes.isekaimahou.feature.structures_b.client;

import com.renanpontes.isekaimahou.feature.structures_b.StructuresBContent;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Parte de cliente do módulo "structures_b": partícula de vaga-lume (os modelos dos blocos são só JSON). */
public class StructuresBClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((RegisterParticleProvidersEvent e) ->
                e.registerSpriteSet(StructuresBContent.FAIRY_FIREFLY.get(), FireflyParticle.Provider::new));
    }
}
