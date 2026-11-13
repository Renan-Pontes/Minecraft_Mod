package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.Util;
import net.minecraft.world.entity.LivingEntity;
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
    /** Nomes das barras de chefe vistas há pouco (nome → último quadro em que apareceu, ms). */
    private static final Map<String, Long> NAMES = new HashMap<>();
    private static final long NAME_MS = 3000L;

    private BossBarTracker() {}

    static void frameStart(RenderGuiEvent.Pre e) {
        lastBarY = -1;
    }

    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress e) {
        lastBarY = Math.max(lastBarY, e.getY());
        String name = e.getBossEvent().getName().getString().strip();
        if (!name.isEmpty()) NAMES.put(name, Util.getMillis());
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

    /**
     * Esta criatura é a dona de uma barra de chefe que está no topo da tela? (mesmo nome da barra). Chefes com barra
     * no topo NUNCA ganham barrinha flutuante: a vida deles fica só lá em cima.
     */
    static boolean ownsTopBar(LivingEntity e) {
        if (NAMES.isEmpty()) return false;
        long now = Util.getMillis();
        NAMES.values().removeIf(t -> now - t > NAME_MS);
        if (NAMES.isEmpty()) return false;
        return NAMES.containsKey(e.getDisplayName().getString().strip()) || NAMES.containsKey(e.getName().getString().strip());
    }

    /** Saiu do mundo: nada de reserva antiga na próxima sessão. */
    static void clear() {
        NAMES.clear();
        lastBarY = -1;
        heldY = -1;
        heldUntil = 0;
    }
}
