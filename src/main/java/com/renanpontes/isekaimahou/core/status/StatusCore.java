package com.renanpontes.isekaimahou.core.status;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Inicialização do núcleo v0.8 — A1: condições de status ({@code core/api/Statuses}), reações
 * ({@code core/ElementReactions}), destruição de terreno ({@code core/api/Destruction}) e lugares
 * ({@code core/api/Places}). Ver docs/V08_DESIGN.md §2 e docs/v08/API_world.md.
 */
public final class StatusCore {
    public static void init(IEventBus modBus) {
        StatusRegistries.register(modBus);
        modBus.addListener(StatusPayloads::register);
        // Regra de jogo na thread principal (o registro de regras do Minecraft não é seguro em paralelo)
        modBus.addListener((FMLCommonSetupEvent e) -> e.enqueueWork(DestructionRules::registerRule));

        IEventBus bus = NeoForge.EVENT_BUS;
        StatusEngine.registerEvents(bus);
        // Sincronização dos ícones
        bus.addListener(StatusSync::flush);
        bus.addListener(StatusSync::onStartTracking);
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> StatusSync.resendSelf(e));
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> StatusSync.resendSelf(e));
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> StatusSync.resendSelf(e));
        // Destruição
        bus.addListener(DestructionQueue::tick);
        bus.addListener(DestructionQueue::onLevelUnload);
        // Lugares
        bus.addListener(PlaceData::onLevelLoad);
        bus.addListener(PlaceData::onLevelUnload);
        bus.addListener(PlaceTracker::onPlayerTick);
        bus.addListener(PlaceTracker::onLogin);
        bus.addListener(PlaceTracker::onLogout);
        bus.addListener(PlaceTracker::onExplosion);
        bus.addListener(PlaceTracker::onSpawnPosition);
        bus.addListener(PlaceTracker::onFinalizeSpawn);
        // Estado estático limpo ao parar o servidor (mundo novo no mesmo jogo começa do zero)
        bus.addListener((ServerStoppedEvent e) -> clearAll());

        StatusCommands.register();
    }

    private static void clearAll() {
        StatusEngine.clear();
        StatusSync.clear();
        ReactionFx.clear();
        DestructionQueue.clear();
        PlaceTracker.clear();
        PlaceData.clearCache();
        DebrisEntity.resetCount();
    }

    private StatusCore() {}
}
