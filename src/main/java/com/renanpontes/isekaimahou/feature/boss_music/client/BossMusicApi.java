package com.renanpontes.isekaimahou.feature.boss_music.client;

import org.jetbrains.annotations.Nullable;

/**
 * SÓ CLIENTE. API para outros módulos pedirem uma música AMBIENTE (ex.: "las_noches" enquanto o jogador está dentro do
 * palácio). Chefes perto sempre têm prioridade sobre o ambiente. Chame a cada tick/segundo enquanto valer; passe null
 * (ou pare de chamar por ~3 s) para soltar. O {@link BossMusicPlayer} lê {@link #currentAmbient()} a cada tick, toca o
 * tema com fade in de 2 s e faz fade out de 3 s quando o pedido expira. Ids desconhecidos são ignorados.
 */
public final class BossMusicApi {
    static volatile @Nullable String ambient;
    static volatile long ambientUntil;

    public static void requestAmbient(@Nullable String theme) {
        ambient = theme;
        ambientUntil = System.currentTimeMillis() + 3000L;
    }

    /** O tema ambiente pedido agora (ou null). */
    public static @Nullable String currentAmbient() {
        return System.currentTimeMillis() < ambientUntil ? ambient : null;
    }

    /** Temas de clima (tocados um pouco mais baixos que os de luta). */
    static boolean isAmbientTheme(String theme) {
        return "las_noches".equals(theme) || "flying_carpet".equals(theme);
    }

    private BossMusicApi() {}
}
