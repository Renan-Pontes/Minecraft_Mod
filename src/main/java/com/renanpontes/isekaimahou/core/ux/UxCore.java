package com.renanpontes.isekaimahou.core.ux;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Inicialização do núcleo v1.0 — A3 (servidor/comum): bloqueio de teleporte de mapa ({@link MapTeleportGuard}),
 * limites de arena ({@code core/api/ArenaBounds} → {@link ArenaService}) e a ordem da entrada no jogo
 * ({@link EntryFlow}). O cliente (menu principal, {@code HudLayout}, borda das arenas) está em {@code client/ux/UxCoreClient}.
 * Ver docs/V10_DESIGN.md §2 A3 e docs/v10/API_ux.md.
 */
public final class UxCore {
    public static void init(IEventBus modBus) {
        modBus.addListener(UxPayloads::register);

        IEventBus bus = NeoForge.EVENT_BUS;
        // Alta prioridade: o comando é cancelado antes de outros mods reagirem a ele
        bus.addListener(EventPriority.HIGH, CommandEvent.class, MapTeleportGuard::onCommand);
        bus.addListener(ArenaService::onLogin);
        bus.addListener(ArenaService::onDimension);
        bus.addListener(ArenaService::onRespawn);
        // Estado estático limpo ao parar o servidor (mundo novo no mesmo jogo começa do zero)
        bus.addListener((ServerStoppedEvent e) -> ArenaService.clear());
        // v1.3: "/mahou perf [segundos]" — quem está pesando no tick do servidor
        com.renanpontes.isekaimahou.core.perf.PerfProfiler.init();
        // v1.6: Modo Paz (trava magias/esquiva/PvP na cidade e em casa)
        PeaceMode.init(modBus);
        // v1.0: senha de administrador (ninguém usa comando de admin sem ela)
        AdminAuth.init(modBus);
    }

    private UxCore() {}
}
