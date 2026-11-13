package com.renanpontes.isekaimahou.feature.travel;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.PlaceEvent;
import com.renanpontes.isekaimahou.feature.travel.guide.GuideCommands;
import com.renanpontes.isekaimahou.feature.travel.guide.GuideNet;
import com.renanpontes.isekaimahou.feature.travel.guide.GuideService;
import com.renanpontes.isekaimahou.feature.travel.guide.MapMarkers;
import com.renanpontes.isekaimahou.feature.travel.guide.XaeroServerProfile;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Módulo "travel" (v0.9): Pedras de Teleporte e a aba "Viagem" do menu B, sobre a API {@code core.api.Travel}.
 *
 * <ul>
 *   <li>Bloco {@code travel_stone}: nas cidades, vilas da Guilda, santuários, portais e covis (colocado pelas
 *       estruturas, protegido) e feito pelo jogador (receita cara; nome pela bigorna ou etiqueta).</li>
 *   <li>Tocar a pedra sintoniza o destino; clicar abre a tela "Viagem" (mapa + lista, custo normal, sem recarga).</li>
 *   <li>A aba "Viagem" (cliente) viaja de qualquer lugar: custo ×3 e recarga de 10 min (regras do núcleo).</li>
 *   <li>v1.0 — <b>Guia de Portais</b> (sub-aba da Viagem): cada dimensão com "como abrir", requisito e "rastrear o mais
 *       próximo" ({@code guide/PortalGuide}, {@code guide/GuideService}); <b>pontos no minimapa</b> do Xaero criados
 *       sozinhos ao descobrir estruturas importantes ({@code guide/MapMarkers}); e o teleporte pelo mapa do Xaero
 *       desligado também no perfil de servidor ({@code guide/XaeroServerProfile}).</li>
 * </ul>
 */
public class TravelModule implements Module {
    @Override
    public String id() {
        return "travel";
    }

    @Override
    public void init(IEventBus modBus) {
        TravelContent.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) e.accept(TravelContent.TRAVEL_STONE_ITEM.get());
        });
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.Counter e) -> TravelStones.onCounter(e));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> TravelStones.onLogout(e));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> TravelStones.clear());

        // v1.0: Guia de Portais + pontos do minimapa
        modBus.addListener(GuideNet::register);
        GuideCommands.register();
        XaeroServerProfile.seed();
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Post e) -> MapMarkers.tick(e.getServer()));
        bus.addListener((PlaceEvent.Enter e) -> MapMarkers.onPlaceEnter(e));
        bus.addListener((MagicProgressEvent.CircleChanged e) -> GuideService.onCircle(e.getPlayer(), e.oldCircle, e.newCircle));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                GuideService.onLogin(sp);
                // Um pouco depois: o minimapa do cliente ainda está abrindo a sessão dele
                MapMarkers.schedule(sp, 60, null);
            }
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) MapMarkers.schedule(sp, 10, null);
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) MapMarkers.schedule(sp, 10, null);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            GuideService.onLogout(e.getEntity().getUUID());
            MapMarkers.onLogout(e.getEntity().getUUID());
        });
        bus.addListener((ServerStoppedEvent e) -> {
            GuideService.clearAll();
            MapMarkers.clearAll();
        });
    }
}
