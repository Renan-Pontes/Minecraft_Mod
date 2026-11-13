package com.renanpontes.isekaimahou.feature.boss_music.client;

import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do módulo "boss_music": liga o {@link BossMusicPlayer} aos eventos do jogo. */
public class BossMusicClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            BossMusicPlayer.tick();
            BossEntranceFx.tick();
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RenderGuiEvent.Post e) ->
                BossEntranceFx.render(e.getGuiGraphics(), e.getPartialTick().getGameTimeDeltaPartialTick(false)));
        // enquanto o tema do chefe toca (e um pouco depois), nada de música do vanilla por cima
        NeoForge.EVENT_BUS.addListener((SelectMusicEvent e) -> {
            if (BossMusicPlayer.suppressVanilla()) e.setMusic(null);
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> BossMusicPlayer.reset());
    }
}
