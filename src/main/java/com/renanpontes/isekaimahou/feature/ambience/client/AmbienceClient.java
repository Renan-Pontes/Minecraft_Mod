package com.renanpontes.isekaimahou.feature.ambience.client;

import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do módulo "ambience": liga a música ambiente e o brilho no escuro aos eventos do jogo. */
public class AmbienceClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> AmbientPlayer.tick());
        NeoForge.EVENT_BUS.addListener((PlaySoundEvent e) -> AmbientPlayer.onSound(e));
        // enquanto a faixa ambiente toca (e um pouco depois), nada de música do vanilla por cima
        NeoForge.EVENT_BUS.addListener((SelectMusicEvent e) -> {
            if (AmbientPlayer.suppressVanilla()) e.setMusic(null);
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> AmbientPlayer.reset());
        modBus.addListener((ModelEvent.ModifyBakingResult e) -> EmissiveModels.onBake(e));
    }
}
