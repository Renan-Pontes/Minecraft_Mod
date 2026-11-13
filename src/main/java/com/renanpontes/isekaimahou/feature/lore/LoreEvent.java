package com.renanpontes.isekaimahou.feature.lore;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Eventos do módulo lore (no {@code NeoForge.EVENT_BUS}, só no servidor) — para o Diário/campanha reagirem. */
public abstract class LoreEvent extends Event {
    private final ServerPlayer player;

    protected LoreEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    /** O jogador ganhou um fragmento novo (página ou estela). {@code setComplete}: fechou a coleção com ele. */
    public static class FragmentFound extends LoreEvent {
        public final LoreFragments.Fragment fragment;
        public final boolean setComplete;

        public FragmentFound(ServerPlayer player, LoreFragments.Fragment fragment, boolean setComplete) {
            super(player);
            this.fragment = fragment;
            this.setComplete = setComplete;
        }
    }
}
