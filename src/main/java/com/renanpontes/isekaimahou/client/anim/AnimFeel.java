package com.renanpontes.isekaimahou.client.anim;

import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.LA;
import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.LL;
import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.RA;
import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.RL;
import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.def;
import static dev.kosmx.playerAnim.core.util.Ease.CONSTANT;
import static dev.kosmx.playerAnim.core.util.Ease.INOUTSINE;
import static dev.kosmx.playerAnim.core.util.Ease.INQUAD;
import static dev.kosmx.playerAnim.core.util.Ease.INSINE;
import static dev.kosmx.playerAnim.core.util.Ease.LINEAR;
import static dev.kosmx.playerAnim.core.util.Ease.OUTBACK;
import static dev.kosmx.playerAnim.core.util.Ease.OUTCUBIC;
import static dev.kosmx.playerAnim.core.util.Ease.OUTSINE;

import com.renanpontes.isekaimahou.client.anim.AnimLibrary.A;
import com.renanpontes.isekaimahou.client.anim.AnimLibrary.Cue;
import com.renanpontes.isekaimahou.client.anim.AnimLibrary.Kind;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;

/**
 * v1.3 — sensação de movimento (tranco, queda e levantar, rolamento/aterrissagem, investida correndo, saque da arma,
 * vitória) e os emotes da roda. Mesmas convenções de {@link AnimLibrary}.
 * <p>
 * Ranks: emote 0 (qualquer coisa interrompe) · saque/investida 1 · tranco 2 · vitória 3 · aterrissagem 4 ·
 * queda 5 (nada interrompe o levantar).
 */
final class AnimFeel {
    static void build() {
        feel();
        emotes();
    }

    private static A base(A a) {
        return a.r(RA, 0, -10, 0, 8, LINEAR).r(LA, 0, -10, 0, -8, LINEAR).bend(RA, 0, 0, LINEAR).bend(LA, 0, 0, LINEAR)
                .b(0, 0, 0, LINEAR).y(0, 0, LINEAR).r(RL, 0, 0, 0, 0, LINEAR).r(LL, 0, 0, 0, 0, LINEAR);
    }

    private static A end(A a, int t) {
        return a.rest(t, INOUTSINE, RA, LA, RL, LL).b(t, 0, 0, INOUTSINE).y(t, 0, INOUTSINE);
    }

    // =====================================================================================
    //  Sensação
    // =====================================================================================
    private static void feel() {
        // ---- Tranco pela frente: o corpo é jogado para trás, braços se abrem
        A ff = base(new A(10, 13))
                .b(2, 14, 0, OUTCUBIC).r(RA, 2, -45, 0, 30, OUTCUBIC).bend(RA, 2, 40, OUTCUBIC).r(LA, 2, -40, 0, -30, OUTCUBIC)
                .bend(LA, 2, 40, OUTCUBIC).r(RL, 2, -12, 0, 0, OUTCUBIC)
                .b(4, 10, 0, OUTSINE);
        def(MahouAnims.FLINCH_F, Kind.FEEL, 2).fade(1).body().free().add(end(ff, 10));
        // ---- Tranco pelas costas: o corpo é jogado para a frente, braços para trás, um passo para não cair
        A fb = base(new A(10, 13))
                .b(2, -16, 0, OUTCUBIC).r(RA, 2, 25, 0, 15, OUTCUBIC).r(LA, 2, 25, 0, -15, OUTCUBIC)
                .r(RL, 2, 10, 0, 0, OUTCUBIC).r(LL, 2, -18, 0, 0, OUTCUBIC)
                .b(4, -12, 0, OUTSINE);
        def(MahouAnims.FLINCH_B, Kind.FEEL, 2).fade(1).body().free().add(end(fb, 10));

        // ---- Queda: arremessado para trás, bate de costas no chão (quica), fica um instante, senta e levanta
        A down = base(new A(46, 50))
                .b(3, 32, 0, OUTCUBIC).y(3, 0.25F, OUTCUBIC).r(RA, 3, -120, 0, 35, OUTCUBIC).bend(RA, 3, 30, OUTCUBIC)
                .r(LA, 3, -120, 0, -35, OUTCUBIC).bend(LA, 3, 30, OUTCUBIC).r(RL, 3, -35, 0, 4, OUTCUBIC).r(LL, 3, -20, 0, -4, OUTCUBIC)
                .b(8, 86, 0, INQUAD).y(8, -0.52F, INQUAD).r(RA, 8, -160, 0, 30, INQUAD).bend(RA, 8, 10, INQUAD)
                .r(LA, 8, -160, 0, -30, INQUAD).bend(LA, 8, 10, INQUAD).r(RL, 8, -10, 0, 6, INQUAD).r(LL, 8, -4, 0, -6, INQUAD)
                .b(10, 80, 0, OUTSINE).y(10, -0.46F, OUTSINE)
                .b(13, 88, 0, INSINE).y(13, -0.55F, INSINE)
                .b(22, 88, 0, LINEAR).y(22, -0.55F, LINEAR).r(RA, 22, -150, 0, 35, LINEAR).r(LA, 22, -150, 0, -35, LINEAR)
                .b(29, 18, 0, INOUTSINE).y(29, -0.58F, INOUTSINE).r(RA, 29, 25, 0, 18, INOUTSINE).bend(RA, 29, 0, INOUTSINE)
                .r(LA, 29, 25, 0, -18, INOUTSINE).bend(LA, 29, 0, INOUTSINE).r(RL, 29, -72, 0, 4, INOUTSINE)
                .r(LL, 29, -68, 0, -4, INOUTSINE)
                .b(37, -22, 0, INOUTSINE).y(37, -0.28F, INOUTSINE).r(RA, 37, -40, -10, 10, INOUTSINE).bend(RA, 37, 30, INOUTSINE)
                .r(LA, 37, -30, 10, -10, INOUTSINE).bend(LA, 37, 20, INOUTSINE).r(RL, 37, -55, 0, 0, INOUTSINE)
                .r(LL, 37, 30, 0, 0, INOUTSINE);
        def(MahouAnims.KNOCKDOWN, Kind.FEEL, 5).fade(1).body().free().cue(Cue.THUD, 8).add(end(down, 46));

        // ---- Rolamento ao aterrissar correndo: amortece agachado e rola para a frente (o corpo gira 360°)
        A roll = new A(13, 14)
                .b(0, 0, 0, LINEAR).y(0, -0.2F, LINEAR).r(RA, 0, -40, 0, 15, LINEAR).r(LA, 0, -40, 0, -15, LINEAR)
                .bend(RA, 0, 30, LINEAR).bend(LA, 0, 30, LINEAR).r(RL, 0, -40, 0, 0, LINEAR).r(LL, 0, -40, 0, 0, LINEAR)
                .r(RA, 2, -70, 0, 20, OUTSINE).r(LA, 2, -70, 0, -20, OUTSINE).bend(RA, 2, 70, OUTSINE).bend(LA, 2, 70, OUTSINE)
                .r(RL, 2, -95, 0, 0, OUTSINE).r(LL, 2, -95, 0, 0, OUTSINE).y(2, -0.15F, OUTSINE)
                .b(6, -180, 0, INSINE).y(6, 0, INSINE)
                .r(RA, 9, -70, 0, 20, LINEAR).r(LA, 9, -70, 0, -20, LINEAR).r(RL, 9, -90, 0, 0, LINEAR).r(LL, 9, -90, 0, 0, LINEAR)
                .b(10, -360, 0, OUTSINE).y(10, -0.05F, OUTSINE)
                .b(11, 0, 0, CONSTANT);
        def(MahouAnims.LAND_ROLL, Kind.FEEL, 4).fade(1).body().free().cue(Cue.ROLL, 1)
                .add(roll.rest(13, INOUTSINE, RA, LA, RL, LL).y(13, 0, INOUTSINE));

        // ---- Aterrissagem de herói: um joelho e um punho no chão, o outro braço aberto para trás; ergue a cabeça e levanta
        A hero = new A(30, 34)
                .b(0, -28, 0, LINEAR).y(0, -0.42F, LINEAR).r(RA, 0, -25, -5, 10, LINEAR).bend(RA, 0, 0, LINEAR)
                .r(LA, 0, -10, 0, -65, LINEAR).bend(LA, 0, 10, LINEAR).r(RL, 0, -75, 0, 4, LINEAR).r(LL, 0, 35, 0, -4, LINEAR)
                .b(14, -26, 0, LINEAR).y(14, -0.4F, LINEAR)
                .b(20, -10, 0, INOUTSINE).y(20, -0.2F, INOUTSINE).r(RA, 20, -20, -5, 10, INOUTSINE).r(LA, 20, -10, 0, -35, INOUTSINE)
                .r(RL, 20, -40, 0, 2, INOUTSINE).r(LL, 20, 20, 0, -2, INOUTSINE);
        def(MahouAnims.LAND_HERO, Kind.FEEL, 4).fade(1).body().free().cue(Cue.LANDING, 0).add(end(hero, 30));

        // ---- Investida correndo: estocada longa com a perna da frente bem à frente (mira)
        A lunge = new A(12, 15)
                .r(RA, 0, -60, 10, 15, LINEAR).bend(RA, 0, 60, LINEAR).r(LA, 0, -20, 0, -15, LINEAR).bend(LA, 0, 0, LINEAR)
                .b(0, 0, 0, LINEAR).y(0, 0, LINEAR).r(RL, 0, 0, 0, 0, LINEAR).r(LL, 0, 0, 0, 0, LINEAR)
                .r(RA, 2, -95, -4, 0, OUTCUBIC).bend(RA, 2, 0, OUTCUBIC).r(LA, 2, 30, 0, -25, OUTCUBIC).b(2, -18, 0, OUTCUBIC)
                .y(2, -0.06F, OUTCUBIC).r(RL, 2, -40, 0, 0, OUTCUBIC).r(LL, 2, 32, 0, 0, OUTCUBIC)
                .b(5, -16, 0, LINEAR);
        def(MahouAnims.SPRINT_LUNGE, Kind.MELEE, 1).fade(1).aim().body().add(end(lunge, 12));

        // ---- Saque de lâmina: floreio para o lado, corte no ar e saudação com a lâmina perto do rosto
        A draw = new A(20, 23)
                .r(RA, 0, -20, 0, 10, LINEAR).bend(RA, 0, 0, LINEAR).r(LA, 0, -10, 0, -10, LINEAR).tw(0, 0, LINEAR)
                .r(RA, 4, -110, 45, 35, OUTSINE).bend(RA, 4, 10, OUTSINE).tw(4, 15, OUTSINE)
                .r(RA, 7, -70, -35, -5, OUTCUBIC).tw(7, -12, OUTCUBIC)
                .r(RA, 10, -115, -8, 5, OUTBACK).bend(RA, 10, 35, OUTCUBIC).r(LA, 10, -15, 0, -15, OUTSINE).tw(10, 0, OUTSINE)
                .r(RA, 14, -113, -8, 5, LINEAR)
                .rest(20, INOUTSINE, RA, LA).tw(20, 0, INOUTSINE);
        def(MahouAnims.DRAW_BLADE, Kind.DRAW, 1).fade(2).cue(Cue.DRAW, 5).add(draw);
        // ---- Saque de arma pesada: ergue com as duas mãos e apoia no ombro
        A heavy = new A(22, 25)
                .r(RA, 0, -15, 0, 10, LINEAR).bend(RA, 0, 0, LINEAR).r(LA, 0, -10, 0, -10, LINEAR).bend(LA, 0, 0, LINEAR)
                .b(0, 0, 0, LINEAR)
                .r(RA, 5, -55, 0, 25, OUTSINE).bend(RA, 5, 30, OUTSINE).r(LA, 5, -50, 30, -5, OUTSINE).bend(LA, 5, 40, OUTSINE)
                .b(5, -6, 0, OUTSINE)
                .r(RA, 10, -160, -25, 25, OUTBACK).bend(RA, 10, 115, OUTCUBIC).r(LA, 10, -20, 0, -12, OUTSINE)
                .bend(LA, 10, 10, OUTSINE).b(10, 5, 0, OUTSINE)
                .r(RA, 16, -158, -25, 25, LINEAR)
                .rest(22, INOUTSINE, RA, LA).b(22, 0, 0, INOUTSINE);
        def(MahouAnims.DRAW_HEAVY, Kind.DRAW, 1).fade(2).cue(Cue.DRAW_HEAVY, 10).add(heavy);

        // ---- Vitória: punho erguido ao céu (com um "pump"), o outro cerrado, base aberta; abaixa devagar
        A win = base(new A(60, 64))
                .r(RA, 6, -176, -6, 12, OUTBACK).bend(RA, 6, 0, OUTCUBIC).r(LA, 6, -25, 0, -22, OUTSINE).bend(LA, 6, 75, OUTSINE)
                .b(6, 8, 0, OUTSINE).y(6, 0.04F, OUTSINE).r(RL, 6, 0, 0, 8, OUTSINE).r(LL, 6, 0, 0, -8, OUTSINE)
                .r(RA, 10, -150, -6, 12, INOUTSINE).bend(RA, 10, 35, INOUTSINE).y(10, 0, INOUTSINE)
                .r(RA, 14, -178, -6, 12, OUTBACK).bend(RA, 14, 0, OUTCUBIC).y(14, 0.06F, OUTCUBIC)
                .y(24, 0, INOUTSINE)
                .r(RA, 40, -176, -6, 12, LINEAR)
                .r(RA, 50, -60, 0, 15, INOUTSINE).bend(RA, 50, 20, INOUTSINE).r(LA, 50, -15, 0, -15, INOUTSINE)
                .bend(LA, 50, 30, INOUTSINE).b(50, 2, 0, INOUTSINE);
        def(MahouAnims.VICTORY, Kind.FEEL, 3).fade(3).body().free().cue(Cue.VICTORY, 7).add(end(win, 60));
    }

    // =====================================================================================
    //  Emotes (rank 0: qualquer golpe/magia interrompe; os contínuos param quando o jogador se mexe)
    // =====================================================================================
    private static void emotes() {
        // ---- Acenar
        A wave = new A(32, 35)
                .r(RA, 0, -10, 0, 8, LINEAR).bend(RA, 0, 0, LINEAR)
                .r(RA, 5, -160, -10, 30, OUTBACK).bend(RA, 5, 25, OUTSINE)
                .r(RA, 9, -160, -10, 55, INOUTSINE).r(RA, 13, -160, -10, 25, INOUTSINE).r(RA, 17, -160, -10, 55, INOUTSINE)
                .r(RA, 21, -160, -10, 25, INOUTSINE).r(RA, 25, -155, -10, 35, INOUTSINE)
                .rest(32, INOUTSINE, RA);
        def(MahouAnims.EMOTE_WAVE, Kind.EMOTE, 0).fade(3).free().body().add(wave);

        // ---- Reverência: mão no peito, a outra para trás, inclina o tronco (as pernas compensam e ficam retas)
        A bow = base(new A(36, 40))
                .b(8, -38, 0, INOUTSINE).y(8, -0.02F, INOUTSINE).r(RL, 8, 36, 0, 0, INOUTSINE).r(LL, 8, 36, 0, 0, INOUTSINE)
                .r(RA, 8, -58, -38, 0, INOUTSINE).bend(RA, 8, 85, INOUTSINE).r(LA, 8, 18, 0, -8, INOUTSINE).bend(LA, 8, 50, INOUTSINE)
                .b(22, -40, 0, LINEAR).r(RL, 22, 38, 0, 0, LINEAR).r(LL, 22, 38, 0, 0, LINEAR);
        def(MahouAnims.EMOTE_BOW, Kind.EMOTE, 0).fade(3).free().body().add(end(bow, 32));

        // ---- Sentar no chão: pernas à frente, mãos apoiadas atrás, balança de leve (contínuo)
        A sit = new A(40, 40).loop(0)
                .b(0, 6, 0, LINEAR).y(0, -0.6F, LINEAR).r(RL, 0, -88, 0, 6, LINEAR).r(LL, 0, -86, 0, -6, LINEAR)
                .r(RA, 0, 20, 0, 15, LINEAR).r(LA, 0, 20, 0, -15, LINEAR).bend(RA, 0, 0, LINEAR).bend(LA, 0, 0, LINEAR)
                .b(20, 8, 0, INOUTSINE).r(RA, 20, 22, 0, 16, INOUTSINE).r(LA, 20, 22, 0, -16, INOUTSINE)
                .b(40, 6, 0, INOUTSINE).r(RA, 40, 20, 0, 15, INOUTSINE).r(LA, 40, 20, 0, -15, INOUTSINE);
        def(MahouAnims.EMOTE_SIT, Kind.EMOTE, 0).fade(6).free().body().hold(0).add(sit);

        // ---- Comemorar: braços para cima com pulinhos
        A cheer = base(new A(40, 44))
                .r(RA, 4, -170, 0, 20, OUTBACK).r(LA, 4, -170, 0, -20, OUTBACK).y(4, 0.12F, OUTSINE)
                .r(RA, 8, -150, 0, 20, INQUAD).bend(RA, 8, 30, INQUAD).r(LA, 8, -150, 0, -20, INQUAD).bend(LA, 8, 30, INQUAD)
                .y(8, 0, INQUAD)
                .r(RA, 12, -175, 0, 20, OUTBACK).bend(RA, 12, 0, OUTSINE).r(LA, 12, -175, 0, -20, OUTBACK).bend(LA, 12, 0, OUTSINE)
                .y(12, 0.14F, OUTSINE)
                .r(RA, 16, -150, 0, 20, INQUAD).bend(RA, 16, 30, INQUAD).r(LA, 16, -150, 0, -20, INQUAD).bend(LA, 16, 30, INQUAD)
                .y(16, 0, INQUAD)
                .r(RA, 20, -178, 0, 20, OUTBACK).bend(RA, 20, 0, OUTSINE).r(LA, 20, -178, 0, -20, OUTBACK).bend(LA, 20, 0, OUTSINE)
                .y(20, 0.14F, OUTSINE)
                .y(24, 0, INQUAD)
                .r(RA, 32, -160, 0, 20, LINEAR).r(LA, 32, -160, 0, -20, LINEAR);
        def(MahouAnims.EMOTE_CHEER, Kind.EMOTE, 0).fade(2).free().body().add(end(cheer, 40));

        // ---- Apontar (segue a mira), a outra mão na cintura
        A point = base(new A(30, 33))
                .r(RA, 4, -92, -4, 0, OUTBACK).r(LA, 4, -5, 10, -40, OUTBACK).bend(LA, 4, 85, OUTCUBIC).b(4, 4, 0, OUTSINE)
                .r(RA, 22, -91, -4, 0, LINEAR);
        def(MahouAnims.EMOTE_POINT, Kind.EMOTE, 0).fade(2).aim().add(end(point, 30));

        // ---- Cruzar os braços (contínuo)
        A cross = new A(40, 40).loop(0)
                .r(RA, 0, -50, -42, 0, LINEAR).bend(RA, 0, 100, LINEAR).r(LA, 0, -46, 42, 0, LINEAR).bend(LA, 0, 100, LINEAR)
                .b(0, 3, 0, LINEAR)
                .r(RA, 20, -52, -42, 0, INOUTSINE).r(LA, 20, -48, 42, 0, INOUTSINE).b(20, 4, 0, INOUTSINE)
                .r(RA, 40, -50, -42, 0, INOUTSINE).r(LA, 40, -46, 42, 0, INOUTSINE).b(40, 3, 0, INOUTSINE);
        def(MahouAnims.EMOTE_CROSS, Kind.EMOTE, 0).fade(5).free().hold(0).add(cross);

        // ---- Dançar: quadril balança, braços alternam apontando para o alto, tronco gira (contínuo)
        A dance = new A(20, 20).loop(0)
                .r(RA, 0, -165, -10, 30, LINEAR).bend(RA, 0, 10, LINEAR).r(LA, 0, -20, 0, -20, LINEAR).bend(LA, 0, 30, LINEAR)
                .b(0, 0, 8, LINEAR).y(0, 0.02F, LINEAR).r(RL, 0, -12, 0, 4, LINEAR).r(LL, 0, 10, 0, -4, LINEAR).tw(0, 15, LINEAR)
                .y(5, 0.06F, INOUTSINE).b(5, 0, 0, INOUTSINE)
                .r(RA, 10, -20, 0, 20, INOUTSINE).bend(RA, 10, 30, INOUTSINE).r(LA, 10, -165, 10, -30, INOUTSINE)
                .bend(LA, 10, 10, INOUTSINE).b(10, 0, -8, INOUTSINE).y(10, 0.02F, INOUTSINE).r(RL, 10, 10, 0, 4, INOUTSINE)
                .r(LL, 10, -12, 0, -4, INOUTSINE).tw(10, -15, INOUTSINE)
                .y(15, 0.06F, INOUTSINE).b(15, 0, 0, INOUTSINE)
                .r(RA, 20, -165, -10, 30, INOUTSINE).bend(RA, 20, 10, INOUTSINE).r(LA, 20, -20, 0, -20, INOUTSINE)
                .bend(LA, 20, 30, INOUTSINE).b(20, 0, 8, INOUTSINE).y(20, 0.02F, INOUTSINE).r(RL, 20, -12, 0, 4, INOUTSINE)
                .r(LL, 20, 10, 0, -4, INOUTSINE).tw(20, 15, INOUTSINE);
        def(MahouAnims.EMOTE_DANCE, Kind.EMOTE, 0).fade(4).free().body().hold(0).add(dance);
    }

    private AnimFeel() {}
}
