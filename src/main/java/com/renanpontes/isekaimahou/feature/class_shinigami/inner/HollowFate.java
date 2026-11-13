package com.renanpontes.isekaimahou.feature.class_shinigami.inner;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;

/**
 * Ponte (v1.3) entre a prova do VAZIO INTERIOR (Mundo Interior do Shinigami) e a classe Hollow (módulo
 * {@code class_hollow}), sem o Shinigami depender da classe nova: o módulo Hollow preenche {@link #hook} no init.
 * Sem gancho (módulo ausente) a prova funciona como antes: perder só leva de volta à praça.
 * <ul>
 *   <li>{@link Hook#offer}: antes da luta, oferece "Lutar" ou "Aceitar o hollow interior" (tela com aviso);</li>
 *   <li>{@link Hook#defeated}: o Shinigami MORREU para o Vazio Interior — a máscara cobre o rosto, o buraco abre e ele
 *   vira Hollow (em vez de voltar à praça).</li>
 * </ul>
 */
public final class HollowFate {
    public interface Hook {
        /**
         * Oferece a escolha antes da luta. {@code fight} começa a luta normal. Devolve false se não deu para oferecer
         * (aí a luta começa direto).
         */
        boolean offer(ServerPlayer sp, Runnable fight);

        /** Perdeu a luta (morte cancelada no Mundo Interior). true = o Hollow tomou conta (quem chamou não faz mais nada). */
        boolean defeated(ServerPlayer sp);
    }

    @Nullable
    public static volatile Hook hook;

    /** Oferece a escolha (ou começa a luta direto se o módulo Hollow não estiver presente). */
    static void offer(ServerPlayer sp, Runnable fight) {
        Hook h = hook;
        if (h == null || !h.offer(sp, fight)) fight.run();
    }

    /** true se o módulo Hollow cuidou da derrota. */
    static boolean defeated(ServerPlayer sp) {
        Hook h = hook;
        return h != null && h.defeated(sp);
    }

    private HollowFate() {}
}
