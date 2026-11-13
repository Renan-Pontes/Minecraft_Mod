package com.renanpontes.isekaimahou.feature.class_hollow;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * v1.4 — API PÚBLICA da Resurrección (a transformação do Arrancar), para outros módulos (ex.: as leis de transformação
 * do {@code transform_laws}) sem mexer no estado interno do Hollow.
 * <ul>
 *   <li>{@link #isReleased}/{@link #release}/{@link #ticksLeft}: consultas (lado do servidor);</li>
 *   <li>{@link #ON_START}/{@link #ON_END}: ouvintes chamados quando a forma liberada começa e acaba (o {@code boolean}
 *   do fim diz se acabou "de verdade", com o preço; false = morreu/saiu/trocou de classe).</li>
 * </ul>
 */
public final class HollowRelease {
    /** Chamado logo depois de a forma liberada começar (efeitos já aplicados). */
    public static final List<BiConsumer<ServerPlayer, Hollow.Release>> ON_START = new CopyOnWriteArrayList<>();
    /** Chamado quando a forma liberada acaba; o boolean = acabou pelo tempo (com o preço do corpo). */
    public static final List<EndListener> ON_END = new CopyOnWriteArrayList<>();

    @FunctionalInterface
    public interface EndListener {
        void ended(ServerPlayer sp, Hollow.Release release, boolean natural);
    }

    /** O jogador está na forma liberada (Resurrección) agora? */
    public static boolean isReleased(@Nullable Player p) {
        return p != null && HollowState.released(p) != null;
    }

    /** O arquétipo liberado agora (null = selado). */
    @Nullable
    public static Hollow.Release release(@Nullable Player p) {
        return p == null ? null : HollowState.released(p);
    }

    /** Ticks que faltam da forma liberada (0 = selado). */
    public static int ticksLeft(@Nullable Player p) {
        return p == null ? 0 : HollowState.releaseTicksLeft(p);
    }

    static void fireStart(ServerPlayer sp, Hollow.Release r) {
        for (BiConsumer<ServerPlayer, Hollow.Release> l : ON_START) {
            try {
                l.accept(sp, r);
            } catch (RuntimeException ex) {
                com.renanpontes.isekaimahou.IsekaiMahou.LOGGER.error("Ouvinte da Resurrección falhou", ex);
            }
        }
    }

    static void fireEnd(ServerPlayer sp, Hollow.Release r, boolean natural) {
        for (EndListener l : ON_END) {
            try {
                l.ended(sp, r, natural);
            } catch (RuntimeException ex) {
                com.renanpontes.isekaimahou.IsekaiMahou.LOGGER.error("Ouvinte do fim da Resurrección falhou", ex);
            }
        }
    }

    private HollowRelease() {}
}
