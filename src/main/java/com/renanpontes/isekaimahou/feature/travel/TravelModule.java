package com.renanpontes.isekaimahou.feature.travel;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Módulo "travel" (v0.9): Pedras de Teleporte e a aba "Viagem" do menu B, sobre a API {@code core.api.Travel}.
 *
 * <ul>
 *   <li>Bloco {@code travel_stone}: nas cidades, vilas da Guilda, santuários, portais e covis (colocado pelas
 *       estruturas, protegido) e feito pelo jogador (receita cara; nome pela bigorna ou etiqueta).</li>
 *   <li>Tocar a pedra sintoniza o destino; clicar abre a tela "Viagem" (mapa + lista, custo normal, sem recarga).</li>
 *   <li>A aba "Viagem" (cliente) viaja de qualquer lugar: custo ×3 e recarga de 10 min (regras do núcleo).</li>
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
    }
}
