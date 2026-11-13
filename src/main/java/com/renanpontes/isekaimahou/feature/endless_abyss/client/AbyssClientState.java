package com.renanpontes.isekaimahou.feature.endless_abyss.client;

import com.renanpontes.isekaimahou.feature.endless_abyss.AbyssNetwork;

/** Último estado do Abismo recebido do servidor (recorde, Sombras disponíveis, ranking) — lido pela aba. */
final class AbyssClientState {
    static volatile AbyssNetwork.Info info = AbyssNetwork.Info.EMPTY;

    private AbyssClientState() {}
}
