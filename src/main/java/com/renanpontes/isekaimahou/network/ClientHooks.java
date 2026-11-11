package com.renanpontes.isekaimahou.network;

import java.util.function.Consumer;

/**
 * "Ganchos" de cliente para pacotes do núcleo cuja exibição é feita por um módulo
 * (ex.: o módulo "cinematic" troca {@link #announce}, {@link #chant} e {@link #cutscene}).
 * Classe comum (sem imports de cliente). Os valores padrão são definidos pelo cliente do núcleo.
 */
public final class ClientHooks {
    public static volatile Consumer<CorePayloads.AnnounceText> announce = p -> {};
    public static volatile Consumer<CorePayloads.Chant> chant = p -> {};
    public static volatile Consumer<CorePayloads.Cutscene> cutscene = p -> {};
    /** Avisado em todo início/fim de conjuração do PRÓPRIO jogador (animações do Player Animator etc.). */
    public static volatile Consumer<CorePayloads.CastState> castState = p -> {};
    /** Toast de recompensa ({@code Rewards.toast}). O cliente do núcleo instala o toast com moldura arcana. */
    public static volatile Consumer<CorePayloads.RewardToast> toast = p -> {};

    private ClientHooks() {}
}
