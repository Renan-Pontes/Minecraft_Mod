package com.renanpontes.isekaimahou.client.anim;

import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.LA;
import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.LL;
import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.RA;
import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.RL;
import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.def;
import static dev.kosmx.playerAnim.core.util.Ease.INOUTSINE;
import static dev.kosmx.playerAnim.core.util.Ease.LINEAR;
import static dev.kosmx.playerAnim.core.util.Ease.OUTBACK;
import static dev.kosmx.playerAnim.core.util.Ease.OUTCUBIC;

import com.renanpontes.isekaimahou.client.anim.AnimLibrary.A;
import com.renanpontes.isekaimahou.client.anim.AnimLibrary.Kind;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;

/**
 * v1.0 — bloqueio com arma/cajado e aparo. Mesmas convenções de {@link AnimLibrary}.
 * <p>
 * Ranks: guarda 1 (um golpe do próprio jogador não toca por cima: atacar solta a guarda no servidor) · aparo 2.
 */
final class AnimGuard {
    static void build() {
        // ---- Guarda: arma atravessada na frente do peito, mão livre apoiando, joelhos levemente dobrados (respira)
        A g = new A(20, 20).loop(0);
        pose(g, 0, 0F, LINEAR);
        pose(g, 10, 1F, INOUTSINE);
        pose(g, 20, 0F, INOUTSINE);
        // Solta sozinha em 10 s: o cliente (e o servidor, para quem assiste) retoca a pose enquanto a tecla está segurada
        def(MahouAnims.GUARD_WEAPON, Kind.GUARD, 1).fade(3).hold(200).add(g);

        // ---- Aparo: a arma varre para fora e para cima num estalo, o corpo acompanha, e volta para a guarda
        A p = new A(9, 11);
        pose(p, 0, 0F, LINEAR);
        p.r(RA, 2, -115, 40, 28, OUTBACK).bend(RA, 2, 15, OUTCUBIC).r(LA, 2, -35, 10, -22, OUTCUBIC).bend(LA, 2, 35, OUTCUBIC)
                .tw(2, -20, OUTCUBIC).b(2, 5, 0, OUTCUBIC)
                .r(RA, 4, -112, 44, 30, LINEAR).tw(4, -22, LINEAR);
        pose(p, 9, 0F, INOUTSINE);
        def(MahouAnims.PARRY, Kind.GUARD, 2).fade(1).add(p);
    }

    /** Pose da guarda no tick {@code t}; {@code breath} 0..1 = pequena variação da respiração. */
    private static void pose(A a, int t, float breath, dev.kosmx.playerAnim.core.util.Ease e) {
        a.r(RA, t, -72 - 3 * breath, -38, 12, e).bend(RA, t, 62 + 4 * breath, e)
                .r(LA, t, -62 - 3 * breath, 32, -10, e).bend(LA, t, 74, e)
                .tw(t, 8, e).b(t, -5 - breath, 0, e)
                .r(RL, t, -8, 0, 3, e).bend(RL, t, 10, e).r(LL, t, 8, 0, -3, e).bend(LL, t, 10, e);
    }

    private AnimGuard() {}
}
