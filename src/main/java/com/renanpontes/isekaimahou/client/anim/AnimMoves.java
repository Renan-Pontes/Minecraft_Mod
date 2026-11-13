package com.renanpontes.isekaimahou.client.anim;

import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.LA;
import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.LL;
import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.RA;
import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.RL;
import static com.renanpontes.isekaimahou.client.anim.AnimLibrary.def;
import static dev.kosmx.playerAnim.core.util.Ease.CONSTANT;
import static dev.kosmx.playerAnim.core.util.Ease.INCUBIC;
import static dev.kosmx.playerAnim.core.util.Ease.INOUTSINE;
import static dev.kosmx.playerAnim.core.util.Ease.INQUAD;
import static dev.kosmx.playerAnim.core.util.Ease.LINEAR;
import static dev.kosmx.playerAnim.core.util.Ease.OUTBACK;
import static dev.kosmx.playerAnim.core.util.Ease.OUTCUBIC;
import static dev.kosmx.playerAnim.core.util.Ease.OUTSINE;

import com.renanpontes.isekaimahou.client.anim.AnimLibrary.A;
import com.renanpontes.isekaimahou.client.anim.AnimLibrary.Cue;
import com.renanpontes.isekaimahou.client.anim.AnimLibrary.Kind;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;

/**
 * v1.3 — transformações, Hollow/Arrancar e técnicas de classe. Todo golpe grande segue o roteiro de anime:
 * <b>preparo</b> (encolhe/ergue, treme), <b>impacto</b> (explode em poucos ticks, com OUTBACK/INQUAD) e
 * <b>rescaldo</b> (segura a pose, respira e volta). Mesmas convenções de {@link AnimLibrary}.
 */
final class AnimMoves {
    static void build() {
        transforms();
        hollow();
        techniques();
    }

    /** Pose neutra das partes usadas (tick 0). */
    private static A base(A a) {
        return a.r(RA, 0, -10, 0, 8, LINEAR).r(LA, 0, -10, 0, -8, LINEAR).bend(RA, 0, 0, LINEAR).bend(LA, 0, 0, LINEAR)
                .b(0, 0, 0, LINEAR).y(0, 0, LINEAR).r(RL, 0, 0, 0, 0, LINEAR).r(LL, 0, 0, 0, 0, LINEAR);
    }

    /** Tudo de volta ao neutro no tick {@code t}. */
    private static A end(A a, int t) {
        return a.rest(t, INOUTSINE, RA, LA, RL, LL).b(t, 0, 0, INOUTSINE).y(t, 0, INOUTSINE);
    }

    // =====================================================================================
    //  Transformações (raças, Encantador, formas)
    // =====================================================================================
    private static void transforms() {
        // ---- Rugido: encolhe tremendo, punhos no peito → explode para trás com os braços abertos e treme no grito
        A roar = base(new A(40, 44))
                .r(RA, 8, -45, -30, 15, INOUTSINE).bend(RA, 8, 100, INOUTSINE).r(LA, 8, -45, 30, -15, INOUTSINE)
                .bend(LA, 8, 100, INOUTSINE).b(8, -24, 0, INOUTSINE).y(8, -0.12F, INOUTSINE)
                .r(RL, 8, -22, 0, 8, INOUTSINE).r(LL, 8, 10, 0, -8, INOUTSINE)
                .r(RA, 11, -40, -35, 12, LINEAR).r(LA, 11, -40, 35, -12, LINEAR).b(11, -28, 0, LINEAR).y(11, -0.15F, LINEAR)
                .r(RA, 14, 25, 0, 75, OUTBACK).bend(RA, 14, 25, OUTCUBIC).r(LA, 14, 25, 0, -75, OUTBACK).bend(LA, 14, 25, OUTCUBIC)
                .b(14, 18, 0, OUTBACK).y(14, 0.04F, OUTCUBIC).r(RL, 14, -8, 0, 14, OUTCUBIC).r(LL, 14, 8, 0, -14, OUTCUBIC)
                .r(RA, 17, 22, 0, 78, LINEAR).r(LA, 17, 22, 0, -78, LINEAR).b(17, 16, 2, LINEAR)
                .b(20, 18, -2, LINEAR).b(23, 16, 2, LINEAR).b(26, 18, -2, LINEAR)
                .r(RA, 29, 20, 0, 74, LINEAR).r(LA, 29, 20, 0, -74, LINEAR).b(29, 17, 0, LINEAR);
        def(MahouAnims.TRANSFORM_ROAR, Kind.SPELL, 4).fade(3).body().cue(Cue.SLAM, 14).add(end(roar, 40));

        // ---- Ascensão: os braços abrem e sobem em V enquanto o corpo flutua e respira no alto
        A rise = base(new A(52, 56))
                .r(RA, 10, -20, 0, 40, OUTSINE).r(LA, 10, -20, 0, -40, OUTSINE).b(10, 4, 0, OUTSINE).y(10, 0.15F, OUTSINE)
                .r(RL, 10, 12, 0, 2, OUTSINE).r(LL, 10, 8, 0, -2, OUTSINE)
                .r(RA, 24, -165, 0, 30, INOUTSINE).r(LA, 24, -165, 0, -30, INOUTSINE).b(24, 12, 0, INOUTSINE)
                .y(24, 0.45F, INOUTSINE).r(RL, 24, 16, 0, 3, INOUTSINE).r(LL, 24, 10, 0, -3, INOUTSINE)
                .y(32, 0.52F, INOUTSINE).b(32, 13, 0, INOUTSINE)
                .y(40, 0.45F, INOUTSINE).b(40, 12, 0, INOUTSINE)
                .r(RA, 44, -160, 0, 32, LINEAR).r(LA, 44, -160, 0, -32, LINEAR).y(44, 0.48F, INOUTSINE);
        def(MahouAnims.TRANSFORM_RISE, Kind.SPELL, 4).fade(3).body().add(end(rise, 52));

        // ---- Explosão de poder: base larga, punhos na cintura tremendo → braços jogados para fora num estouro
        A burst = base(new A(34, 38))
                .r(RA, 6, 20, 0, 25, INOUTSINE).bend(RA, 6, 90, INOUTSINE).r(LA, 6, 20, 0, -25, INOUTSINE).bend(LA, 6, 90, INOUTSINE)
                .b(6, -10, 0, INOUTSINE).y(6, -0.1F, INOUTSINE).r(RL, 6, 0, 0, 16, INOUTSINE).r(LL, 6, 0, 0, -16, INOUTSINE)
                .b(9, -12, 2, LINEAR).bend(RA, 9, 95, LINEAR).bend(LA, 9, 95, LINEAR)
                .b(12, -10, -2, LINEAR).y(12, -0.12F, LINEAR)
                .r(RA, 15, -15, 0, 60, OUTBACK).bend(RA, 15, 10, OUTCUBIC).r(LA, 15, -15, 0, -60, OUTBACK).bend(LA, 15, 10, OUTCUBIC)
                .b(15, 14, 0, OUTBACK).y(15, 0.05F, OUTCUBIC).r(RL, 15, 0, 0, 18, OUTCUBIC).r(LL, 15, 0, 0, -18, OUTCUBIC)
                .r(RA, 22, -12, 0, 62, LINEAR).r(LA, 22, -12, 0, -62, LINEAR).b(22, 12, 0, LINEAR);
        def(MahouAnims.TRANSFORM_BURST, Kind.SPELL, 4).fade(2).body().cue(Cue.SLAM, 15).add(end(burst, 34));
    }

    // =====================================================================================
    //  Hollow / Arrancar
    // =====================================================================================
    private static void hollow() {
        // ---- Máscara: a mão agarra o rosto e arrasta para baixo (a máscara se forma), corpo curvado tremendo,
        //      depois ergue a cabeça e joga o braço para o lado
        A mask = base(new A(38, 42))
                .r(RA, 5, -155, -25, 0, OUTCUBIC).bend(RA, 5, 115, OUTCUBIC).r(LA, 5, 5, 0, -20, OUTSINE).bend(LA, 5, 30, OUTSINE)
                .b(5, -6, 0, OUTSINE)
                .r(RA, 9, -160, -28, 0, LINEAR).bend(RA, 9, 120, LINEAR).b(9, -8, 0, LINEAR)
                .r(RA, 14, -95, -15, 10, INCUBIC).bend(RA, 14, 50, INCUBIC).b(14, -22, 0, INCUBIC)
                .r(LA, 14, 10, 0, -28, INCUBIC).bend(LA, 14, 40, INCUBIC).r(RL, 14, -10, 0, 8, INCUBIC).r(LL, 14, 8, 0, -8, INCUBIC)
                .r(RA, 17, -80, -10, 10, OUTSINE).bend(RA, 17, 40, OUTSINE).b(17, -26, 0, OUTSINE)
                .b(20, -24, 3, LINEAR).b(23, -24, -3, LINEAR)
                .r(RA, 27, -40, 0, 70, OUTBACK).bend(RA, 27, 10, OUTCUBIC).r(LA, 27, -30, 0, -50, OUTBACK).bend(LA, 27, 10, OUTCUBIC)
                .b(27, 10, 0, OUTBACK)
                .b(31, 9, 0, LINEAR);
        def(MahouAnims.HOLLOW_MASK_TEAR, Kind.SPELL, 4).fade(2).body().add(end(mask, 38));

        // ---- Cero carregando: braço esticado (mira), a outra mão segura o pulso, base firme e tremor (contínuo, até 5 s)
        A ceroCharge = new A(16, 16).loop(0)
                .r(RA, 0, -90, -6, 0, LINEAR).bend(RA, 0, 0, LINEAR).r(LA, 0, -72, 38, 0, LINEAR).bend(LA, 0, 50, LINEAR)
                .b(0, -5, 0, LINEAR).r(RL, 0, -14, 0, 4, LINEAR).r(LL, 0, 12, 0, -4, LINEAR)
                .r(RA, 4, -88, -7, 2, INOUTSINE).b(4, -6, 1, INOUTSINE)
                .r(RA, 8, -91, -5, -1, INOUTSINE).b(8, -5, -1, INOUTSINE)
                .r(RA, 12, -89, -6, 1, INOUTSINE).b(12, -6, 0, INOUTSINE)
                .r(RA, 16, -90, -6, 0, INOUTSINE).b(16, -5, 0, INOUTSINE);
        def(MahouAnims.CERO_CHARGE, Kind.SPELL, 3).fade(3).aim().hold(100).add(ceroCharge);

        // ---- Cero disparado: o coice joga o braço para cima e o corpo para trás, depois assenta
        A ceroFire = new A(22, 26)
                .r(RA, 0, -90, -6, 0, LINEAR).bend(RA, 0, 0, LINEAR).r(LA, 0, -72, 38, 0, LINEAR).bend(LA, 0, 50, LINEAR)
                .b(0, -5, 0, LINEAR).y(0, 0, LINEAR).r(RL, 0, -14, 0, 4, LINEAR).r(LL, 0, 12, 0, -4, LINEAR)
                .r(RA, 2, -118, -6, 0, OUTCUBIC).r(LA, 2, -80, 30, 0, OUTCUBIC).b(2, 10, 0, OUTCUBIC).y(2, 0.02F, OUTCUBIC)
                .r(RA, 5, -100, -6, 0, OUTSINE).b(5, 4, 0, OUTSINE)
                .r(RA, 10, -95, -6, 0, LINEAR).b(10, 2, 0, LINEAR);
        def(MahouAnims.CERO_FIRE, Kind.SPELL, 3).fade(1).aim().add(end(ceroFire, 22));

        // ---- Sonído: some num passo baixo e inclinado (mais curto e seco que o avanço comum)
        A sonido = new A(9, 12)
                .r(RA, 0, 0, 0, 5, LINEAR).r(LA, 0, 0, 0, -5, LINEAR).b(0, 0, 0, LINEAR).y(0, 0, LINEAR)
                .r(RL, 0, 0, 0, 0, LINEAR).r(LL, 0, 0, 0, 0, LINEAR)
                .r(RA, 2, 30, 0, 12, OUTCUBIC).r(LA, 2, 30, 0, -12, OUTCUBIC).b(2, -22, 0, OUTCUBIC).y(2, -0.06F, OUTCUBIC)
                .r(RL, 2, -30, 0, 0, OUTCUBIC).r(LL, 2, 22, 0, 0, OUTCUBIC)
                .b(5, -18, 0, LINEAR);
        def(MahouAnims.SONIDO_STEP, Kind.SPELL, 2).fade(1).body().add(end(sonido, 9));

        // ---- Resurrección: lâmina erguida à frente, a outra mão sobre ela (chamando o nome) → estouro, flutua e assenta
        A res = base(new A(60, 64))
                .r(RA, 8, -100, -12, 0, OUTSINE).r(LA, 8, -95, 30, 0, OUTSINE).bend(LA, 8, 25, OUTSINE).b(8, -4, 0, OUTSINE)
                .r(RA, 20, -104, -12, 0, LINEAR).r(LA, 20, -98, 30, 0, LINEAR).b(20, -6, 0, LINEAR)
                .r(RA, 24, -110, -10, 0, INQUAD).r(LA, 24, -100, 28, 0, INQUAD)
                .r(RA, 27, -40, 0, 80, OUTBACK).bend(RA, 27, 10, OUTCUBIC).r(LA, 27, -40, 0, -80, OUTBACK).bend(LA, 27, 10, OUTCUBIC)
                .b(27, 16, 0, OUTBACK).y(27, 0.18F, OUTCUBIC).r(RL, 27, 10, 0, 10, OUTCUBIC).r(LL, 27, 10, 0, -10, OUTCUBIC)
                .y(33, 0.24F, LINEAR).b(33, 15, 2, LINEAR)
                .y(39, 0.22F, LINEAR).b(39, 16, -2, LINEAR)
                .y(45, 0.24F, LINEAR).b(45, 15, 1, LINEAR)
                .r(RA, 50, -25, 0, 50, OUTSINE).r(LA, 50, -25, 0, -50, OUTSINE).b(50, 6, 0, OUTSINE).y(50, 0.1F, OUTSINE);
        def(MahouAnims.RESURRECCION, Kind.SPELL, 4).fade(3).body().cue(Cue.SLAM, 27).add(end(res, 60));
    }

    // =====================================================================================
    //  Técnicas (arco, arremesso, giro, pancada no chão, ajoelhar, provocar, espada sagrada, escudo)
    // =====================================================================================
    private static void techniques() {
        // ---- Arco gigante: estica o arco, puxa a corda até a orelha com o corpo inclinado, treme segurando e solta
        A bow = new A(36, 40)
                .r(RA, 0, -40, -10, 10, LINEAR).r(LA, 0, -40, 10, -10, LINEAR).bend(RA, 0, 0, LINEAR).bend(LA, 0, 0, LINEAR)
                .tw(0, 0, LINEAR).b(0, 0, 0, LINEAR).r(RL, 0, 0, 0, 0, LINEAR).r(LL, 0, 0, 0, 0, LINEAR)
                .r(LA, 5, -92, 8, 0, OUTSINE).r(RA, 5, -92, -12, 0, OUTSINE).tw(5, 10, OUTSINE)
                .r(RA, 11, -92, -50, 0, INOUTSINE).bend(RA, 11, 30, INOUTSINE).tw(11, 18, INOUTSINE).b(11, 4, 0, INOUTSINE)
                .r(RL, 11, -8, 0, 10, INOUTSINE).r(LL, 11, 10, 0, -12, INOUTSINE)
                .r(RA, 14, -91, -52, 1, LINEAR).r(LA, 14, -93, 8, 0, LINEAR)
                .r(RA, 17, -92, -50, -1, LINEAR).r(LA, 17, -91, 8, 0, LINEAR)
                .r(RA, 20, -91, -53, 1, LINEAR).r(LA, 20, -93, 8, 0, LINEAR)
                .r(RA, 23, -92, -52, 0, LINEAR).r(LA, 23, -92, 8, 0, LINEAR)
                .r(RA, 26, -70, 45, 35, OUTCUBIC).bend(RA, 26, 0, OUTCUBIC).r(LA, 26, -88, 8, 0, OUTCUBIC).tw(26, -8, OUTCUBIC)
                .b(26, -4, 0, OUTCUBIC)
                .r(LA, 29, -90, 8, 0, LINEAR)
                .tw(36, 0, INOUTSINE);
        def(MahouAnims.BOW_CHARGE_BIG, Kind.SPELL, 2).fade(2).aim().add(end(bow, 36));

        // ---- Arremesso por cima: braço vai atrás da cabeça, passo à frente e solta com o corpo todo
        A thr = new A(16, 19)
                .r(RA, 0, -30, 0, 10, LINEAR).bend(RA, 0, 0, LINEAR).r(LA, 0, -10, 0, -10, LINEAR).bend(LA, 0, 0, LINEAR)
                .tw(0, 0, LINEAR).b(0, 0, 0, LINEAR).y(0, 0, LINEAR).r(RL, 0, 0, 0, 0, LINEAR).r(LL, 0, 0, 0, 0, LINEAR)
                .r(RA, 4, -155, 15, 20, OUTSINE).bend(RA, 4, 70, OUTSINE).r(LA, 4, -80, 15, -10, OUTSINE).tw(4, 22, OUTSINE)
                .b(4, 5, 0, OUTSINE).r(RL, 4, 12, 0, 0, OUTSINE).r(LL, 4, -16, 0, 0, OUTSINE)
                .r(RA, 7, -70, -20, 0, OUTCUBIC).bend(RA, 7, 0, OUTCUBIC).r(LA, 7, -25, 0, -25, OUTCUBIC).tw(7, -20, OUTCUBIC)
                .b(7, -12, 0, OUTCUBIC).r(RL, 7, -14, 0, 0, OUTCUBIC).r(LL, 7, 14, 0, 0, OUTCUBIC)
                .r(RA, 10, -35, -30, 5, OUTSINE).tw(10, -24, OUTSINE).b(10, -10, 0, OUTSINE)
                .tw(16, 0, INOUTSINE);
        def(MahouAnims.THROW_OVERHAND, Kind.SPELL, 2).fade(1).add(end(thr, 16));

        // ---- Corte giratório: arma para o lado, o corpo dá uma volta inteira (no fim pula de 360 para 0 = mesma pose)
        A spin = new A(18, 21)
                .r(RA, 0, -75, 50, 30, LINEAR).bend(RA, 0, 0, LINEAR).r(LA, 0, -20, 0, -25, LINEAR).bend(LA, 0, 0, LINEAR)
                .tw(0, 22, LINEAR).spin(0, 0, 0, 0, LINEAR).y(0, 0, LINEAR).r(RL, 0, 0, 0, 0, LINEAR).r(LL, 0, 0, 0, 0, LINEAR)
                .spin(2, -4, -25, 0, OUTSINE).r(RA, 2, -80, 60, 40, OUTSINE).tw(2, 25, OUTSINE).y(2, -0.05F, OUTSINE)
                .r(RL, 2, -10, 0, 8, OUTSINE).r(LL, 2, 10, 0, -8, OUTSINE)
                .r(RA, 4, -85, -10, 85, OUTCUBIC).r(LA, 4, -30, 0, -50, OUTCUBIC)
                .spin(9, -8, 360, 0, OUTCUBIC)
                .spin(10, -8, 0, 0, CONSTANT)
                .r(RA, 12, -60, -40, 20, OUTSINE).tw(12, -20, OUTSINE)
                .tw(18, 0, INOUTSINE);
        // end() zera o corpo inteiro (pitch, giro e roll) no tick 18
        def(MahouAnims.SPIN_SLASH, Kind.SPELL, 2).fade(1).body().add(end(spin, 18));

        // ---- Pancada no chão: pulinho com os punhos juntos lá no alto → desce com tudo numa base larga (poeira no impacto)
        A slam = base(new A(26, 30))
                .r(RA, 5, -172, 8, 8, OUTSINE).bend(RA, 5, 20, OUTSINE).r(LA, 5, -172, -8, -8, OUTSINE).bend(LA, 5, 20, OUTSINE)
                .b(5, 10, 0, OUTSINE).y(5, 0.35F, OUTSINE).r(RL, 5, -35, 0, 0, OUTSINE).r(LL, 5, -10, 0, 0, OUTSINE)
                .r(RA, 7, -176, 8, 8, LINEAR).r(LA, 7, -176, -8, -8, LINEAR).y(7, 0.38F, LINEAR).b(7, 11, 0, LINEAR)
                .r(RA, 10, -35, -15, 10, INQUAD).bend(RA, 10, 0, INQUAD).r(LA, 10, -35, 15, -10, INQUAD).bend(LA, 10, 0, INQUAD)
                .b(10, -32, 0, INQUAD).y(10, -0.22F, INQUAD).r(RL, 10, -45, 0, 6, INQUAD).r(LL, 10, 30, 0, -6, INQUAD)
                .r(RA, 13, -28, -15, 10, OUTSINE).r(LA, 13, -28, 15, -10, OUTSINE).b(13, -34, 0, OUTSINE).y(13, -0.25F, OUTSINE)
                .b(20, -30, 0, LINEAR).y(20, -0.22F, LINEAR);
        def(MahouAnims.GROUND_SLAM, Kind.SPELL, 2).fade(2).body().cue(Cue.SLAM, 10).add(end(slam, 26));

        // ---- Ajoelhar canalizando: um joelho no chão, mãos juntas no peito respirando (~3 s) e levanta sozinho
        A kneel = base(new A(70, 74))
                .r(RA, 8, -55, -35, 0, OUTSINE).bend(RA, 8, 65, OUTSINE).r(LA, 8, -55, 35, 0, OUTSINE).bend(LA, 8, 65, OUTSINE)
                .b(8, -8, 0, OUTSINE).y(8, -0.32F, OUTSINE).r(RL, 8, -62, 0, 4, OUTSINE).r(LL, 8, 38, 0, -4, OUTSINE)
                .b(20, -10, 0, INOUTSINE).r(RA, 20, -58, -36, 0, INOUTSINE).r(LA, 20, -58, 36, 0, INOUTSINE)
                .b(32, -8, 0, INOUTSINE).r(RA, 32, -55, -35, 0, INOUTSINE).r(LA, 32, -55, 35, 0, INOUTSINE)
                .b(44, -10, 0, INOUTSINE).r(RA, 44, -58, -36, 0, INOUTSINE).r(LA, 44, -58, 36, 0, INOUTSINE)
                .b(56, -8, 0, INOUTSINE).r(RA, 56, -55, -35, 0, INOUTSINE).r(LA, 56, -55, 35, 0, INOUTSINE).y(56, -0.32F, INOUTSINE)
                .r(RL, 56, -62, 0, 4, INOUTSINE).r(LL, 56, 38, 0, -4, INOUTSINE)
                .b(62, -6, 0, INOUTSINE).y(62, -0.15F, INOUTSINE).r(RL, 62, -30, 0, 2, INOUTSINE).r(LL, 62, 20, 0, -2, INOUTSINE);
        def(MahouAnims.KNEEL_CHANNEL, Kind.SPELL, 2).fade(4).body().free().add(end(kneel, 70));

        // ---- Provocar: mão na cintura, a outra chama com os dedos ("vem!"), peito estufado
        A taunt = base(new A(30, 33))
                .r(RA, 4, -82, -8, 0, OUTBACK).r(LA, 4, -5, 10, -40, OUTBACK).bend(LA, 4, 85, OUTCUBIC).b(4, 6, 0, OUTSINE)
                .bend(RA, 8, 60, OUTSINE).bend(RA, 12, 5, INOUTSINE).bend(RA, 16, 60, OUTSINE).bend(RA, 20, 5, INOUTSINE)
                .r(RA, 24, -80, -8, 0, LINEAR).b(24, 6, 0, LINEAR);
        def(MahouAnims.TAUNT, Kind.SPELL, 1).fade(3).add(end(taunt, 30));

        // ---- Espada sagrada (Excalibur/Balmung/Caliburn): ergue com as duas mãos, inclina para trás carregando e
        //      desce num corte vertical com passo largo (poeira quando a lâmina "bate")
        A cleave = base(new A(32, 36))
                .r(RA, 6, -172, -12, 0, OUTSINE).bend(RA, 6, 15, OUTSINE).r(LA, 6, -172, 12, 0, OUTSINE).bend(LA, 6, 15, OUTSINE)
                .b(6, 10, 0, OUTSINE).y(6, 0.04F, OUTSINE).r(RL, 6, -14, 0, 4, OUTSINE).r(LL, 6, 10, 0, -4, OUTSINE)
                .r(RA, 12, -178, -12, 0, LINEAR).r(LA, 12, -178, 12, 0, LINEAR).b(12, 12, 0, LINEAR).y(12, 0.06F, LINEAR)
                .r(RA, 15, -40, -12, 0, INCUBIC).bend(RA, 15, 0, INCUBIC).r(LA, 15, -40, 12, 0, INCUBIC).bend(LA, 15, 0, INCUBIC)
                .b(15, -24, 0, INCUBIC).y(15, -0.12F, INCUBIC).r(RL, 15, -38, 0, 4, INCUBIC).r(LL, 15, 26, 0, -4, INCUBIC)
                .r(RA, 18, -32, -12, 0, OUTSINE).r(LA, 18, -32, 12, 0, OUTSINE).b(18, -26, 0, OUTSINE)
                .b(24, -24, 0, LINEAR);
        def(MahouAnims.HOLY_CLEAVE, Kind.SPELL, 3).fade(2).body().cue(Cue.SLAM, 15).add(end(cleave, 32));

        // ---- Palma de escudo: mão aberta à frente com a outra segurando o antebraço, base firme (mira)
        A ward = new A(24, 27)
                .r(RA, 0, -40, 0, 10, LINEAR).bend(RA, 0, 40, LINEAR).r(LA, 0, -20, 0, -10, LINEAR).bend(LA, 0, 0, LINEAR)
                .b(0, 0, 0, LINEAR).y(0, 0, LINEAR).r(RL, 0, 0, 0, 0, LINEAR).r(LL, 0, 0, 0, 0, LINEAR)
                .r(RA, 4, -95, -4, 0, OUTBACK).bend(RA, 4, 0, OUTCUBIC).r(LA, 4, -72, 36, 0, OUTCUBIC).bend(LA, 4, 45, OUTCUBIC)
                .b(4, -8, 0, OUTCUBIC).r(RL, 4, -14, 0, 4, OUTCUBIC).r(LL, 4, 12, 0, -4, OUTCUBIC)
                .r(RA, 16, -93, -4, 0, LINEAR).b(16, -7, 0, LINEAR);
        def(MahouAnims.PALM_WARD, Kind.SPELL, 2).fade(2).aim().add(end(ward, 24));
    }

    private AnimMoves() {}
}
