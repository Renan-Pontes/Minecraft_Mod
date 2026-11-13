package com.renanpontes.isekaimahou.feature.home_realm.client;

import com.renanpontes.isekaimahou.feature.home_realm.HomeNetwork;

import net.minecraft.Util;

/** Último estado da Dimensão Casa recebido do servidor (aba "Lar" e tecla). */
public final class HomeClientState {
    static HomeNetwork.Info info = HomeNetwork.Info.EMPTY;
    private static long receivedAt;

    static void accept(HomeNetwork.Info i) {
        info = i;
        receivedAt = Util.getMillis();
    }

    static void clear() {
        info = HomeNetwork.Info.EMPTY;
        receivedAt = 0;
    }

    /** Segundos que ainda faltam para completar os 2 min fora de combate (contados aqui desde a última resposta). */
    static int combatSecondsLeft() {
        long leftMs = info.combatTicks() * 50L - (Util.getMillis() - receivedAt);
        return leftMs <= 0 ? 0 : (int) ((leftMs + 999) / 1000);
    }

    /** A aba só aparece para quem tem um lar, foi convidado para um ou está lá agora (menu limpo para os outros). */
    static boolean relevant() {
        return info.owned() || info.inRealm() || !info.visitable().isEmpty();
    }

    private HomeClientState() {}
}
