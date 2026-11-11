package com.renanpontes.isekaimahou.core.reputation;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Inicialização do núcleo v0.8 — A3: reputação e facções. Ver docs/V08_DESIGN.md §4 e docs/v08/API_reputation.md.
 * API pública: {@link com.renanpontes.isekaimahou.core.api.Reputation}.
 */
public final class ReputationCore {
    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, ReputationPayloads::register);
        ReputationEvents.register(NeoForge.EVENT_BUS);
        ReputationCommands.register();
    }

    private ReputationCore() {}
}
