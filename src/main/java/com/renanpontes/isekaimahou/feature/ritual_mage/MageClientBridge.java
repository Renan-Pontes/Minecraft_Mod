package com.renanpontes.isekaimahou.feature.ritual_mage;

/**
 * Ganchos que o lado cliente preenche (RitualMageClient). O código comum chama só estes Runnables,
 * então nenhuma classe de cliente é carregada no servidor dedicado.
 */
public final class MageClientBridge {
    /** Abre a tela do Atlas Arcano (no servidor dedicado continua vazio). */
    public static Runnable openAtlas = () -> {};

    private MageClientBridge() {}
}
