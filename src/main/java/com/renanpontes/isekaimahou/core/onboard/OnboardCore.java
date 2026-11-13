package com.renanpontes.isekaimahou.core.onboard;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Inicialização do núcleo v0.9 — A2: diálogo ({@code core/api/Dialogue}), entrada no jogo ({@code core/api/Onboarding})
 * e viagem rápida ({@code core/api/Travel}). Ver docs/V09_DESIGN.md §3 e docs/v09/API_player.md.
 */
public final class OnboardCore {
    public static void init(IEventBus modBus) {
        modBus.addListener(OnboardPayloads::register);

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(OnboardTasks::tick);
        // Entrada no jogo
        bus.addListener(OnboardService::onLogin);
        bus.addListener(OnboardService::onPlayerTick);
        // Diálogo
        bus.addListener(DialogueService::onCast);
        bus.addListener(DialogueService::onLogout);
        // Viagem
        bus.addListener(TravelService::onPlaceEnter);
        bus.addListener(TravelService::onDimension);
        bus.addListener(TravelService::onLogout);
        bus.addListener(OnboardService::onLogout);
        // v1.0: quem ainda está criando o personagem fica protegido e a tela certa reabre sozinha
        EntryGuard.init(bus);
        // Estado estático limpo ao parar o servidor (mundo novo no mesmo jogo começa do zero)
        bus.addListener((ServerStoppedEvent e) -> {
            OnboardTasks.clear();
            EntryGuard.clear();
            DialogueService.clear();
            TravelService.clear();
        });

        OnboardCommands.register();
    }

    private OnboardCore() {}
}
