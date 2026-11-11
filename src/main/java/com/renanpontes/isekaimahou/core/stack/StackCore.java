package com.renanpontes.isekaimahou.core.stack;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Inicialização do núcleo v0.8 — A2: a Pilha (contramágica, {@code core/api/SpellStack}), eventos de dano de magia
 * ({@code SpellDamageEvent}), API de domínios ({@code core/api/Domains}) e usos diários ({@code MagicApi.tryUseDaily}).
 * Ver docs/V08_DESIGN.md §3 e docs/v08/API_stack.md.
 */
public final class StackCore {
    public static void init(IEventBus modBus) {
        modBus.addListener(StackPayloads::register);
        NeoForge.EVENT_BUS.addListener(StackManager::onServerTick);
        NeoForge.EVENT_BUS.addListener(StackManager::onLogin);
        NeoForge.EVENT_BUS.addListener(StackManager::onLogout);
        NeoForge.EVENT_BUS.addListener(StackManager::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(StackManager::onServerStopped);
        NeoForge.EVENT_BUS.addListener(DomainRegistry::onServerTick);
        NeoForge.EVENT_BUS.addListener(DomainRegistry::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(DomainRegistry::onServerStopped);
        NeoForge.EVENT_BUS.addListener(DamageCapture::onDamagePost);
        NeoForge.EVENT_BUS.addListener(StackCore::onServerStopped);
        StackCommands.register();
    }

    private static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        DamageCapture.clear();
    }

    private StackCore() {}
}
