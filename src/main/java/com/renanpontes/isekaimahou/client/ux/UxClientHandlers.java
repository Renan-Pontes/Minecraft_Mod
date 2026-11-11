package com.renanpontes.isekaimahou.client.ux;

import com.renanpontes.isekaimahou.core.ux.UxPayloads;

/** Pacotes do núcleo A3 (UX) chegando no cliente. Só chamado de dentro dos lambdas de registro (servidor dedicado não carrega). */
public final class UxClientHandlers {
    public static void arenaSync(UxPayloads.ArenaSync p) {
        ClientArenas.apply(p);
    }

    private UxClientHandlers() {}
}
