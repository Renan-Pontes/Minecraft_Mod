package com.renanpontes.isekaimahou.feature.ui_hud.client;

import net.minecraft.Util;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Onde terminam as barras de chefe do vanilla NESTE quadro (o Olho Analítico e os banners ficam logo abaixo).
 * A camada das barras desenha antes das camadas do HUD, então o valor já vale quando o painel é desenhado.
 * <p>
 * Uma barra que APARECE empurra o painel na hora (senão ele ficaria por cima dela). Uma barra que SOME só libera o
 * espaço depois de {@link #RELEASE_MS}: os chefes mostram a barra por distância, e quem fica andando na borda do
 * alcance faria a barra piscar — e o painel subir e descer junto. Com a espera, o painel muda no máximo uma vez.
 */
final class BossBarTracker {
    /** Quanto tempo o espaço de uma barra que sumiu continua reservado. */
    private static final long RELEASE_MS = 1500L;

    /** Y da última barra de chefe do quadro, desenhada ou escondida por outro mod (-1 = nenhuma). */
    private static int lastBarY = -1;
    /** Y reservado (o maior visto recentemente) e até quando ele vale se as barras diminuírem. */
    private static int heldY = -1;
    private static long heldUntil;

    private BossBarTracker() {}

    static void frameStart(RenderGuiEvent.Pre e) {
        lastBarY = -1;
    }

    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress e) {
        lastBarY = Math.max(lastBarY, e.getY());
    }

    /** Y efetivo: cresce na hora, diminui só depois da espera. */
    private static int effectiveY() {
        long now = Util.getMillis();
        if (lastBarY >= heldY) {
            heldY = lastBarY;
            if (lastBarY >= 0) heldUntil = now + RELEASE_MS;
        } else if (now >= heldUntil) {
            heldY = lastBarY;
            heldUntil = now + RELEASE_MS;
        }
        return heldY;
    }

    /** Há barra de chefe na tela neste quadro (ou uma que acabou de sumir e ainda reserva o espaço)? */
    static boolean hasBars() {
        return effectiveY() >= 0;
    }

    /** Primeira linha livre no topo central (abaixo da última barra de chefe, ou o topo da tela). */
    static int freeTop() {
        int y = effectiveY();
        return y < 0 ? 4 : y + 9;
    }

    /** Saiu do mundo: nada de reserva antiga na próxima sessão. */
    static void clear() {
        lastBarY = -1;
        heldY = -1;
        heldUntil = 0;
    }
}
