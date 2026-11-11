package com.renanpontes.isekaimahou.core.event;

import com.renanpontes.isekaimahou.core.api.Places;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Um jogador entrou/saiu de um {@link Places.Place} (checado a cada 20 ticks). Postado em NeoForge.EVENT_BUS no
 * servidor. Ao trocar direto de um lugar para outro, vem primeiro o {@link Leave} do antigo e depois o {@link Enter}.
 */
public abstract class PlaceEvent extends Event {
    private final ServerPlayer player;
    private final Places.Place place;

    protected PlaceEvent(ServerPlayer player, Places.Place place) {
        this.player = player;
        this.place = place;
    }

    public ServerPlayer getPlayer() { return player; }
    public Places.Place getPlace() { return place; }

    public static class Enter extends PlaceEvent {
        private final boolean firstVisit;

        public Enter(ServerPlayer player, Places.Place place, boolean firstVisit) {
            super(player, place);
            this.firstVisit = firstVisit;
        }

        /** Primeira vez que este jogador entra neste lugar (flag {@code visit.place.<id>}). */
        public boolean isFirstVisit() { return firstVisit; }
    }

    public static class Leave extends PlaceEvent {
        public Leave(ServerPlayer player, Places.Place place) {
            super(player, place);
        }
    }
}
