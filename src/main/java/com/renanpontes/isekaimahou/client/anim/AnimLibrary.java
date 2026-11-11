package com.renanpontes.isekaimahou.client.anim;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;

import dev.kosmx.playerAnim.core.data.AnimationFormat;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;

/**
 * Todas as animações de combate, construídas em código com o construtor de keyframes do Player Animator
 * (nada para carregar de arquivo; não quebra se um pacote de recursos faltar).
 * <p>
 * Convenções (graus, espaço do modelo do Minecraft):
 * <ul>
 *   <li>braço: pitch -90 = esticado para a frente, -180 = para cima; yaw + = aponta para a DIREITA do jogador;
 *       roll + no braço direito (e − no esquerdo) = abre para o lado; bend = dobra o cotovelo.</li>
 *   <li>perna: pitch − = para a frente; roll igual aos braços (direita +, esquerda − = abrir).</li>
 *   <li>{@code body} = o jogador inteiro (pivô ~0,7 bloco): pitch − = inclina para a frente, roll + = inclina para a
 *       esquerda; y = sobe (blocos).</li>
 *   <li>{@code torso}: só o yaw (torção leve, até ~25°).</li>
 * </ul>
 */
final class AnimLibrary {
    enum Kind { MELEE, SPELL, DODGE, GUARD }

    /**
     * @param rank     prioridade (uma animação só interrompe outra de rank menor ou igual)
     * @param fadeIn   ticks de transição na entrada
     * @param aim      os braços esticados seguem a arfagem da câmera (mira para cima/baixo)
     * @param hold     pose contínua (canalização/carga/guarda) — só sai com "parar"
     * @param faceLook o corpo vira para onde o jogador olha enquanto toca
     * @param mirror   espelha para canhotos
     */
    record Def(String id, KeyframeAnimation anim, Kind kind, int rank, int fadeIn, boolean aim, boolean hold, boolean faceLook,
               boolean mirror) {}

    private static final String RA = "rightArm", LA = "leftArm", RL = "rightLeg", LL = "leftLeg", T = "torso", B = "body";
    private static final Map<String, Def> DEFS = new HashMap<>();

    @Nullable
    static Def get(String id) {
        return DEFS.get(id);
    }

    static boolean isGuard(@Nullable Def d) {
        return d != null && d.kind == Kind.GUARD;
    }

    static void init() {
        if (!DEFS.isEmpty()) return;
        try {
            melee();
            spells();
            dodges();
            guards();
        } catch (RuntimeException e) {
            // Uma animação malformada não pode derrubar o jogo: as que já entraram continuam valendo
            IsekaiMahou.LOGGER.error("[anim] falha ao construir animações de combate", e);
        }
    }

    // =====================================================================================
    //  Corpo a corpo
    // =====================================================================================
    private static void melee() {
        // ---- Espada/katana: 1) corte horizontal (direita → esquerda)
        add(MahouAnims.SWORD_1, Kind.MELEE, 1, 1, false, false, true, true, new A(10, 13)
                .r(RA, 0, -75, 55, 30, Ease.LINEAR).r(LA, 0, -25, 0, -10, Ease.LINEAR).tw(0, 18, Ease.LINEAR)
                .r(RA, 3, -95, -55, -5, Ease.OUTCUBIC).r(LA, 3, -15, 0, -15, Ease.OUTCUBIC).tw(3, -18, Ease.OUTCUBIC)
                .r(RA, 5, -92, -72, -10, Ease.OUTSINE).tw(5, -22, Ease.OUTSINE)
                .r(RA, 10, -25, -15, 5, Ease.INOUTSINE).r(LA, 10, -5, 0, -6, Ease.INOUTSINE).tw(10, -4, Ease.INOUTSINE));
        // ---- 2) corte diagonal (alto-esquerda → baixo-direita)
        add(MahouAnims.SWORD_2, Kind.MELEE, 1, 1, false, false, true, true, new A(11, 14)
                .r(RA, 0, -160, -30, -15, Ease.LINEAR).r(LA, 0, -35, 15, -10, Ease.LINEAR).tw(0, -15, Ease.LINEAR).b(0, 0, 0, Ease.LINEAR)
                .r(RA, 3, -45, 40, 25, Ease.OUTCUBIC).tw(3, 15, Ease.OUTCUBIC).b(3, -6, 0, Ease.OUTCUBIC)
                .r(RA, 5, -30, 50, 30, Ease.OUTSINE).b(5, -7, 0, Ease.OUTSINE)
                .r(RA, 11, -15, 10, 5, Ease.INOUTSINE).r(LA, 11, -5, 0, -5, Ease.INOUTSINE).tw(11, 0, Ease.INOUTSINE)
                .b(11, 0, 0, Ease.INOUTSINE));
        // ---- 3) estocada (mira)
        add(MahouAnims.SWORD_3, Kind.MELEE, 1, 1, true, false, true, true, new A(13, 16)
                .r(RA, 0, -55, 15, 15, Ease.LINEAR).bend(RA, 0, 70, Ease.LINEAR).r(LA, 0, -55, 30, -10, Ease.LINEAR)
                .bend(LA, 0, 40, Ease.LINEAR).tw(0, 18, Ease.LINEAR).b(0, 0, 0, Ease.LINEAR)
                .r(RA, 2, -50, 20, 18, Ease.OUTSINE).bend(RA, 2, 80, Ease.OUTSINE)
                .r(RA, 4, -92, -4, 0, Ease.OUTBACK).bend(RA, 4, 0, Ease.OUTCUBIC).r(LA, 4, -20, 0, -25, Ease.OUTCUBIC)
                .bend(LA, 4, 10, Ease.OUTCUBIC).tw(4, -15, Ease.OUTCUBIC).b(4, -9, 0, Ease.OUTCUBIC)
                .r(RA, 8, -90, -4, 0, Ease.LINEAR).b(8, -8, 0, Ease.LINEAR)
                .r(RA, 13, -20, 0, 5, Ease.INOUTSINE).bend(RA, 13, 10, Ease.INOUTSINE).r(LA, 13, -5, 0, -5, Ease.INOUTSINE)
                .bend(LA, 13, 0, Ease.INOUTSINE).tw(13, 0, Ease.INOUTSINE).b(13, 0, 0, Ease.INOUTSINE));

        // ---- Pesadas (machado/martelo/espadão): 1) golpe por cima que bate no chão
        add(MahouAnims.HEAVY_1, Kind.MELEE, 1, 2, false, false, true, true, new A(16, 20)
                .r(RA, 0, -150, -10, 5, Ease.LINEAR).r(LA, 0, -150, 10, -5, Ease.LINEAR).bend(RA, 0, 40, Ease.LINEAR)
                .bend(LA, 0, 40, Ease.LINEAR).b(0, 4, 0, Ease.LINEAR).r(RL, 0, 0, 0, 0, Ease.LINEAR).r(LL, 0, 0, 0, 0, Ease.LINEAR)
                .r(RA, 4, -175, -5, 5, Ease.OUTSINE).r(LA, 4, -175, 5, -5, Ease.OUTSINE).bend(RA, 4, 30, Ease.OUTSINE)
                .bend(LA, 4, 30, Ease.OUTSINE).b(4, 7, 0, Ease.OUTSINE)
                .r(RA, 7, -45, -10, 0, Ease.INCUBIC).r(LA, 7, -45, 15, 0, Ease.INCUBIC).bend(RA, 7, 5, Ease.INCUBIC)
                .bend(LA, 7, 5, Ease.INCUBIC).b(7, -14, 0, Ease.INCUBIC).r(RL, 7, -25, 0, 4, Ease.INCUBIC)
                .r(LL, 7, 18, 0, -4, Ease.INCUBIC)
                .r(RA, 9, -35, -10, 0, Ease.OUTSINE).r(LA, 9, -35, 15, 0, Ease.OUTSINE).b(9, -16, 0, Ease.OUTSINE)
                .r(RA, 16, -10, 0, 5, Ease.INOUTSINE).r(LA, 16, -10, 0, -5, Ease.INOUTSINE).bend(RA, 16, 0, Ease.INOUTSINE)
                .bend(LA, 16, 0, Ease.INOUTSINE).b(16, 0, 0, Ease.INOUTSINE).r(RL, 16, 0, 0, 0, Ease.INOUTSINE)
                .r(LL, 16, 0, 0, 0, Ease.INOUTSINE));
        // ---- 2) varrida larga com as duas mãos (direita → esquerda)
        add(MahouAnims.HEAVY_2, Kind.MELEE, 1, 2, false, false, true, true, new A(15, 18)
                .r(RA, 0, -70, 65, 25, Ease.LINEAR).r(LA, 0, -75, 75, 0, Ease.LINEAR).bend(RA, 0, 30, Ease.LINEAR)
                .bend(LA, 0, 30, Ease.LINEAR).tw(0, 20, Ease.LINEAR).b(0, 0, 0, Ease.LINEAR)
                .r(RA, 3, -65, 75, 30, Ease.OUTSINE).r(LA, 3, -70, 85, 0, Ease.OUTSINE).tw(3, 24, Ease.OUTSINE)
                .r(RA, 6, -90, -65, -10, Ease.OUTCUBIC).r(LA, 6, -90, -40, -15, Ease.OUTCUBIC).tw(6, -20, Ease.OUTCUBIC)
                .b(6, -5, 0, Ease.OUTCUBIC)
                .r(RA, 8, -88, -80, -12, Ease.OUTSINE).r(LA, 8, -88, -55, -18, Ease.OUTSINE).tw(8, -24, Ease.OUTSINE)
                .r(RA, 15, -15, -10, 5, Ease.INOUTSINE).r(LA, 15, -10, 0, -5, Ease.INOUTSINE).bend(RA, 15, 0, Ease.INOUTSINE)
                .bend(LA, 15, 0, Ease.INOUTSINE).tw(15, 0, Ease.INOUTSINE).b(15, 0, 0, Ease.INOUTSINE));

        // ---- Adagas: estocadas rápidas alternando as mãos (mira)
        add(MahouAnims.DAGGER_1, Kind.MELEE, 1, 1, true, false, true, true, new A(7, 10)
                .r(RA, 0, -50, 10, 15, Ease.LINEAR).bend(RA, 0, 80, Ease.LINEAR).r(LA, 0, -40, 20, -10, Ease.LINEAR)
                .bend(LA, 0, 60, Ease.LINEAR).tw(0, 12, Ease.LINEAR).b(0, 0, 0, Ease.LINEAR)
                .r(RA, 2, -95, -8, 0, Ease.OUTCUBIC).bend(RA, 2, 0, Ease.OUTCUBIC).tw(2, -12, Ease.OUTCUBIC).b(2, -5, 0, Ease.OUTCUBIC)
                .r(RA, 3, -93, -8, 0, Ease.LINEAR)
                .r(RA, 7, -40, 0, 10, Ease.INOUTSINE).bend(RA, 7, 40, Ease.INOUTSINE).r(LA, 7, -35, 15, -10, Ease.INOUTSINE)
                .bend(LA, 7, 50, Ease.INOUTSINE).tw(7, 0, Ease.INOUTSINE).b(7, 0, 0, Ease.INOUTSINE));
        add(MahouAnims.DAGGER_2, Kind.MELEE, 1, 1, true, false, true, true, new A(7, 10)
                .r(LA, 0, -50, -10, -15, Ease.LINEAR).bend(LA, 0, 80, Ease.LINEAR).r(RA, 0, -40, -20, 10, Ease.LINEAR)
                .bend(RA, 0, 60, Ease.LINEAR).tw(0, -12, Ease.LINEAR).b(0, 0, 0, Ease.LINEAR)
                .r(LA, 2, -95, 8, 0, Ease.OUTCUBIC).bend(LA, 2, 0, Ease.OUTCUBIC).tw(2, 12, Ease.OUTCUBIC).b(2, -5, 0, Ease.OUTCUBIC)
                .r(LA, 3, -93, 8, 0, Ease.LINEAR)
                .r(LA, 7, -40, 0, -10, Ease.INOUTSINE).bend(LA, 7, 40, Ease.INOUTSINE).r(RA, 7, -35, -15, 10, Ease.INOUTSINE)
                .bend(RA, 7, 50, Ease.INOUTSINE).tw(7, 0, Ease.INOUTSINE).b(7, 0, 0, Ease.INOUTSINE));

        // ---- Lança/tridente: 1) estocada com as duas mãos (mira), 2) varrida com a haste
        add(MahouAnims.SPEAR_1, Kind.MELEE, 1, 1, true, false, true, true, new A(13, 16)
                .r(RA, 0, -45, 20, 15, Ease.LINEAR).bend(RA, 0, 50, Ease.LINEAR).r(LA, 0, -70, 35, -5, Ease.LINEAR)
                .bend(LA, 0, 30, Ease.LINEAR).tw(0, 22, Ease.LINEAR).b(0, 0, 0, Ease.LINEAR).r(RL, 0, 0, 0, 0, Ease.LINEAR)
                .r(LL, 0, 0, 0, 0, Ease.LINEAR)
                .r(RA, 2, -40, 25, 18, Ease.OUTSINE).bend(RA, 2, 60, Ease.OUTSINE).tw(2, 25, Ease.OUTSINE)
                .r(RA, 5, -88, -5, 0, Ease.OUTBACK).bend(RA, 5, 0, Ease.OUTCUBIC).r(LA, 5, -90, 15, 0, Ease.OUTCUBIC)
                .bend(LA, 5, 0, Ease.OUTCUBIC).tw(5, -10, Ease.OUTCUBIC).b(5, -8, 0, Ease.OUTCUBIC)
                .r(RL, 5, -20, 0, 0, Ease.OUTCUBIC).r(LL, 5, 15, 0, 0, Ease.OUTCUBIC)
                .r(RA, 9, -86, -5, 0, Ease.LINEAR).b(9, -7, 0, Ease.LINEAR)
                .r(RA, 13, -20, 0, 5, Ease.INOUTSINE).r(LA, 13, -10, 0, -5, Ease.INOUTSINE).bend(RA, 13, 0, Ease.INOUTSINE)
                .bend(LA, 13, 0, Ease.INOUTSINE).tw(13, 0, Ease.INOUTSINE).b(13, 0, 0, Ease.INOUTSINE)
                .r(RL, 13, 0, 0, 0, Ease.INOUTSINE).r(LL, 13, 0, 0, 0, Ease.INOUTSINE));
        add(MahouAnims.SPEAR_2, Kind.MELEE, 1, 1, false, false, true, true, new A(12, 15)
                .r(RA, 0, -80, -50, -10, Ease.LINEAR).r(LA, 0, -80, -20, 0, Ease.LINEAR).tw(0, -20, Ease.LINEAR)
                .r(RA, 4, -85, 55, 15, Ease.OUTCUBIC).r(LA, 4, -85, 80, 0, Ease.OUTCUBIC).tw(4, 20, Ease.OUTCUBIC)
                .r(RA, 6, -82, 65, 20, Ease.OUTSINE).r(LA, 6, -82, 88, 0, Ease.OUTSINE).tw(6, 24, Ease.OUTSINE)
                .r(RA, 12, -15, 5, 5, Ease.INOUTSINE).r(LA, 12, -10, 0, -5, Ease.INOUTSINE).tw(12, 0, Ease.INOUTSINE));

        // ---- Punhos: jab, gancho, gancho de baixo (guarda alta entre os golpes)
        add(MahouAnims.FIST_1, Kind.MELEE, 1, 1, true, false, true, true, guard(new A(8, 11), 0)
                .r(RA, 2, -92, -6, 0, Ease.OUTCUBIC).bend(RA, 2, 0, Ease.OUTCUBIC).tw(2, -15, Ease.OUTCUBIC)
                .r(RA, 4, -90, -6, 0, Ease.LINEAR)
                .r(RA, 8, -75, -20, 10, Ease.INOUTSINE).bend(RA, 8, 95, Ease.INOUTSINE).tw(8, 0, Ease.INOUTSINE));
        add(MahouAnims.FIST_2, Kind.MELEE, 1, 1, false, false, true, true, guard(new A(9, 12), 0)
                .r(LA, 1, -80, -25, -25, Ease.OUTSINE).tw(1, -10, Ease.OUTSINE)
                .r(LA, 4, -88, 40, -5, Ease.OUTCUBIC).bend(LA, 4, 85, Ease.OUTCUBIC).tw(4, 22, Ease.OUTCUBIC)
                .r(LA, 5, -86, 45, -5, Ease.LINEAR)
                .r(LA, 9, -75, 20, -10, Ease.INOUTSINE).bend(LA, 9, 95, Ease.INOUTSINE).tw(9, 0, Ease.INOUTSINE));
        add(MahouAnims.FIST_3, Kind.MELEE, 1, 1, false, false, true, true, guard(new A(10, 13), 0).b(0, 0, 0, Ease.LINEAR)
                .r(RA, 2, -25, -10, 10, Ease.OUTSINE).bend(RA, 2, 110, Ease.OUTSINE).tw(2, 10, Ease.OUTSINE).b(2, -5, 0, Ease.OUTSINE)
                .r(RA, 5, -155, -15, 0, Ease.OUTBACK).bend(RA, 5, 50, Ease.OUTCUBIC).tw(5, -12, Ease.OUTCUBIC).b(5, 6, 0, Ease.OUTCUBIC)
                .r(RA, 6, -152, -15, 0, Ease.LINEAR)
                .r(RA, 10, -75, -20, 10, Ease.INOUTSINE).bend(RA, 10, 95, Ease.INOUTSINE).tw(10, 0, Ease.INOUTSINE)
                .b(10, 0, 0, Ease.INOUTSINE));
    }

    /** Guarda de boxe (as duas mãos na frente do rosto) no tick {@code t}. */
    private static A guard(A a, int t) {
        return a.r(RA, t, -75, -20, 10, Ease.LINEAR).bend(RA, t, 95, Ease.LINEAR).r(LA, t, -75, 20, -10, Ease.LINEAR)
                .bend(LA, t, 95, Ease.LINEAR).tw(t, 0, Ease.LINEAR);
    }

    // =====================================================================================
    //  Magia
    // =====================================================================================
    private static void spells() {
        // ---- Projétil: mão estendida (mira)
        add(MahouAnims.CAST_BOLT, Kind.SPELL, 2, 2, true, false, true, true, new A(12, 15)
                .r(RA, 0, -40, 25, 15, Ease.LINEAR).bend(RA, 0, 50, Ease.LINEAR).r(LA, 0, -10, 0, -10, Ease.LINEAR)
                .tw(0, 15, Ease.LINEAR)
                .r(RA, 3, -98, -5, 0, Ease.OUTBACK).bend(RA, 3, 0, Ease.OUTCUBIC).r(LA, 3, 10, 0, -18, Ease.OUTCUBIC)
                .tw(3, -15, Ease.OUTCUBIC)
                .r(RA, 8, -92, -5, 0, Ease.OUTSINE).tw(8, -12, Ease.OUTSINE)
                .r(RA, 12, -20, 0, 5, Ease.INOUTSINE).bend(RA, 12, 0, Ease.INOUTSINE).r(LA, 12, 0, 0, -5, Ease.INOUTSINE)
                .tw(12, 0, Ease.INOUTSINE));
        // ---- Raio/feixe: empurrão com as duas mãos (mira)
        add(MahouAnims.CAST_BEAM, Kind.SPELL, 2, 2, true, false, true, true, new A(14, 18)
                .r(RA, 0, -55, -25, 10, Ease.LINEAR).bend(RA, 0, 70, Ease.LINEAR).r(LA, 0, -55, 25, -10, Ease.LINEAR)
                .bend(LA, 0, 70, Ease.LINEAR).b(0, 3, 0, Ease.LINEAR)
                .r(RA, 4, -92, -12, 0, Ease.OUTBACK).bend(RA, 4, 0, Ease.OUTCUBIC).r(LA, 4, -92, 12, 0, Ease.OUTBACK)
                .bend(LA, 4, 0, Ease.OUTCUBIC).b(4, -6, 0, Ease.OUTCUBIC)
                .r(RA, 10, -90, -12, 0, Ease.LINEAR).r(LA, 10, -90, 12, 0, Ease.LINEAR)
                .r(RA, 14, -25, 0, 5, Ease.INOUTSINE).r(LA, 14, -25, 0, -5, Ease.INOUTSINE).b(14, 0, 0, Ease.INOUTSINE));
        // ---- Raio canalizado: mãos à frente tremendo (contínuo, mira)
        add(MahouAnims.CAST_BEAM_HOLD, Kind.SPELL, 2, 4, true, true, true, true, new A(12, 12).loop(0)
                .r(RA, 0, -90, -12, 0, Ease.LINEAR).r(LA, 0, -90, 12, 0, Ease.LINEAR).bend(RA, 0, 0, Ease.LINEAR)
                .bend(LA, 0, 0, Ease.LINEAR).b(0, -5, 0, Ease.LINEAR)
                .r(RA, 6, -88, -14, 3, Ease.INOUTSINE).r(LA, 6, -88, 14, -3, Ease.INOUTSINE).b(6, -6, 0, Ease.INOUTSINE)
                .r(RA, 12, -90, -12, 0, Ease.INOUTSINE).r(LA, 12, -90, 12, 0, Ease.INOUTSINE).b(12, -5, 0, Ease.INOUTSINE));
        // ---- Área: braços sobem e batem para baixo
        add(MahouAnims.CAST_AREA, Kind.SPELL, 2, 2, false, false, true, true, new A(20, 24)
                .r(RA, 0, -30, 0, 20, Ease.LINEAR).r(LA, 0, -30, 0, -20, Ease.LINEAR).b(0, 0, 0, Ease.LINEAR)
                .r(RL, 0, 0, 0, 0, Ease.LINEAR).r(LL, 0, 0, 0, 0, Ease.LINEAR)
                .r(RA, 6, -170, -10, 15, Ease.OUTSINE).r(LA, 6, -170, 10, -15, Ease.OUTSINE).b(6, 6, 0, Ease.OUTSINE)
                .r(RA, 9, -175, -8, 12, Ease.LINEAR).r(LA, 9, -175, 8, -12, Ease.LINEAR).b(9, 7, 0, Ease.LINEAR)
                .r(RA, 12, -40, -15, 15, Ease.INCUBIC).r(LA, 12, -40, 15, -15, Ease.INCUBIC).b(12, -18, 0, Ease.INCUBIC)
                .r(RL, 12, -28, 0, 5, Ease.INCUBIC).r(LL, 12, 22, 0, -5, Ease.INCUBIC)
                .r(RA, 14, -30, -15, 18, Ease.OUTSINE).r(LA, 14, -30, 15, -18, Ease.OUTSINE).b(14, -20, 0, Ease.OUTSINE)
                .r(RA, 20, -10, 0, 5, Ease.INOUTSINE).r(LA, 20, -10, 0, -5, Ease.INOUTSINE).b(20, 0, 0, Ease.INOUTSINE)
                .r(RL, 20, 0, 0, 0, Ease.INOUTSINE).r(LL, 20, 0, 0, 0, Ease.INOUTSINE));
        // ---- Área forte: pulinho, braços bem altos e pancada no chão
        add(MahouAnims.CAST_AREA_BIG, Kind.SPELL, 2, 2, false, false, true, true, new A(26, 30)
                .r(RA, 0, -30, 0, 20, Ease.LINEAR).r(LA, 0, -30, 0, -20, Ease.LINEAR).b(0, 0, 0, Ease.LINEAR).y(0, 0, Ease.LINEAR)
                .r(RL, 0, 0, 0, 0, Ease.LINEAR).r(LL, 0, 0, 0, 0, Ease.LINEAR)
                .r(RA, 7, -178, -5, 20, Ease.OUTSINE).r(LA, 7, -178, 5, -20, Ease.OUTSINE).b(7, 9, 0, Ease.OUTSINE)
                .y(7, 0.3F, Ease.OUTSINE).r(RL, 7, -30, 0, 3, Ease.OUTSINE).r(LL, 7, -15, 0, -3, Ease.OUTSINE)
                .r(RA, 10, -182, -5, 18, Ease.LINEAR).r(LA, 10, -182, 5, -18, Ease.LINEAR).b(10, 10, 0, Ease.LINEAR)
                .y(10, 0.38F, Ease.OUTSINE)
                .r(RA, 13, -35, -15, 18, Ease.INCUBIC).r(LA, 13, -35, 15, -18, Ease.INCUBIC).b(13, -24, 0, Ease.INCUBIC)
                .y(13, -0.08F, Ease.INCUBIC).r(RL, 13, -35, 0, 6, Ease.INCUBIC).r(LL, 13, 25, 0, -6, Ease.INCUBIC)
                .r(RA, 17, -28, -15, 20, Ease.OUTSINE).r(LA, 17, -28, 15, -20, Ease.OUTSINE).b(17, -25, 0, Ease.OUTSINE)
                .y(17, -0.06F, Ease.OUTSINE)
                .r(RA, 26, -10, 0, 5, Ease.INOUTSINE).r(LA, 26, -10, 0, -5, Ease.INOUTSINE).b(26, 0, 0, Ease.INOUTSINE)
                .y(26, 0, Ease.INOUTSINE).r(RL, 26, 0, 0, 0, Ease.INOUTSINE).r(LL, 26, 0, 0, 0, Ease.INOUTSINE));
        // ---- Aura/em si mesmo: mãos no peito e abre os braços
        add(MahouAnims.CAST_AURA, Kind.SPELL, 2, 2, false, false, false, true, new A(20, 24)
                .r(RA, 0, -20, 0, 10, Ease.LINEAR).r(LA, 0, -20, 0, -10, Ease.LINEAR).bend(RA, 0, 0, Ease.LINEAR)
                .bend(LA, 0, 0, Ease.LINEAR).b(0, 0, 0, Ease.LINEAR)
                .r(RA, 5, -70, -45, 0, Ease.OUTSINE).r(LA, 5, -70, 45, 0, Ease.OUTSINE).bend(RA, 5, 85, Ease.OUTSINE)
                .bend(LA, 5, 85, Ease.OUTSINE)
                .r(RA, 9, -72, -48, 0, Ease.LINEAR).r(LA, 9, -72, 48, 0, Ease.LINEAR)
                .r(RA, 12, -50, 0, 70, Ease.OUTBACK).r(LA, 12, -50, 0, -70, Ease.OUTBACK).bend(RA, 12, 10, Ease.OUTCUBIC)
                .bend(LA, 12, 10, Ease.OUTCUBIC).b(12, 8, 0, Ease.OUTCUBIC)
                .r(RA, 16, -48, 0, 72, Ease.LINEAR).r(LA, 16, -48, 0, -72, Ease.LINEAR).b(16, 8, 0, Ease.LINEAR)
                .r(RA, 20, -10, 0, 8, Ease.INOUTSINE).r(LA, 20, -10, 0, -8, Ease.INOUTSINE).bend(RA, 20, 0, Ease.INOUTSINE)
                .bend(LA, 20, 0, Ease.INOUTSINE).b(20, 0, 0, Ease.INOUTSINE));
        // ---- Invocação: braço erguido para o céu
        add(MahouAnims.CAST_SUMMON, Kind.SPELL, 2, 2, false, false, false, true, new A(20, 24)
                .r(RA, 0, -30, 0, 10, Ease.LINEAR).r(LA, 0, -10, 0, -10, Ease.LINEAR).bend(RA, 0, 20, Ease.LINEAR)
                .b(0, 0, 0, Ease.LINEAR)
                .r(RA, 5, -178, -5, 5, Ease.OUTBACK).bend(RA, 5, 0, Ease.OUTCUBIC).r(LA, 5, -25, 0, -30, Ease.OUTSINE)
                .b(5, 5, 0, Ease.OUTSINE)
                .r(RA, 9, -165, -5, 5, Ease.INOUTSINE).bend(RA, 9, 15, Ease.INOUTSINE)
                .r(RA, 12, -180, -5, 5, Ease.OUTBACK).bend(RA, 12, 0, Ease.OUTCUBIC)
                .r(RA, 16, -178, -5, 5, Ease.LINEAR)
                .r(RA, 20, -10, 0, 5, Ease.INOUTSINE).r(LA, 20, -5, 0, -5, Ease.INOUTSINE).b(20, 0, 0, Ease.INOUTSINE));
        // ---- Arco/flecha: estica o braço do arco, puxa a corda e solta
        add(MahouAnims.CAST_BOW, Kind.SPELL, 2, 2, true, false, true, true, new A(18, 22)
                .r(RA, 0, -40, -10, 10, Ease.LINEAR).r(LA, 0, -40, 10, -10, Ease.LINEAR)
                .r(LA, 4, -92, 8, 0, Ease.OUTSINE).r(RA, 4, -92, -32, 0, Ease.OUTSINE)
                .r(LA, 10, -92, 8, 0, Ease.LINEAR).r(RA, 10, -90, -36, 0, Ease.LINEAR)
                .r(RA, 12, -70, 45, 35, Ease.OUTCUBIC)
                .r(LA, 14, -90, 8, 0, Ease.LINEAR)
                .r(RA, 18, -15, 0, 5, Ease.INOUTSINE).r(LA, 18, -15, 0, -5, Ease.INOUTSINE));
        // ---- Corte mágico (Getsuga, Excalibur...): diagonal grande com avanço
        add(MahouAnims.CAST_SLASH, Kind.SPELL, 2, 2, false, false, true, true, new A(14, 18)
                .r(RA, 0, -170, -35, -15, Ease.LINEAR).r(LA, 0, -60, 20, -20, Ease.LINEAR).tw(0, -20, Ease.LINEAR)
                .b(0, 5, 0, Ease.LINEAR).r(RL, 0, 0, 0, 0, Ease.LINEAR).r(LL, 0, 0, 0, 0, Ease.LINEAR)
                .r(RA, 4, -35, 50, 30, Ease.OUTCUBIC).r(LA, 4, -20, 0, -30, Ease.OUTCUBIC).tw(4, 20, Ease.OUTCUBIC)
                .b(4, -10, 0, Ease.OUTCUBIC).r(RL, 4, -22, 0, 0, Ease.OUTCUBIC).r(LL, 4, 16, 0, 0, Ease.OUTCUBIC)
                .r(RA, 7, -25, 55, 32, Ease.OUTSINE).b(7, -11, 0, Ease.OUTSINE)
                .r(RA, 14, -15, 5, 5, Ease.INOUTSINE).r(LA, 14, -5, 0, -5, Ease.INOUTSINE).tw(14, 0, Ease.INOUTSINE)
                .b(14, 0, 0, Ease.INOUTSINE).r(RL, 14, 0, 0, 0, Ease.INOUTSINE).r(LL, 14, 0, 0, 0, Ease.INOUTSINE));
        // ---- Deslocamento (Shunpo, passo das sombras): corrida ninja inclinada, braços para trás
        add(MahouAnims.CAST_DASH, Kind.SPELL, 2, 1, false, false, true, true, new A(12, 15)
                .r(RA, 0, 0, 0, 5, Ease.LINEAR).r(LA, 0, 0, 0, -5, Ease.LINEAR).b(0, 0, 0, Ease.LINEAR)
                .r(RL, 0, 0, 0, 0, Ease.LINEAR).r(LL, 0, 0, 0, 0, Ease.LINEAR)
                .r(RA, 2, 45, 0, 15, Ease.OUTCUBIC).r(LA, 2, 45, 0, -15, Ease.OUTCUBIC).b(2, -25, 0, Ease.OUTCUBIC)
                .r(RL, 2, -35, 0, 0, Ease.OUTCUBIC).r(LL, 2, 25, 0, 0, Ease.OUTCUBIC)
                .r(RA, 8, 40, 0, 15, Ease.LINEAR).r(LA, 8, 40, 0, -15, Ease.LINEAR).b(8, -22, 0, Ease.LINEAR)
                .r(RA, 12, 0, 0, 5, Ease.INOUTSINE).r(LA, 12, 0, 0, -5, Ease.INOUTSINE).b(12, 0, 0, Ease.INOUTSINE)
                .r(RL, 12, 0, 0, 0, Ease.INOUTSINE).r(LL, 12, 0, 0, 0, Ease.INOUTSINE));
        // ---- Carregando (tempo de conjuração): mãos juntas na frente do peito, pulsando (contínuo)
        add(MahouAnims.CAST_GATHER, Kind.SPELL, 2, 4, false, true, false, true, new A(16, 16).loop(0)
                .r(RA, 0, -55, -35, 0, Ease.LINEAR).r(LA, 0, -55, 35, 0, Ease.LINEAR).bend(RA, 0, 60, Ease.LINEAR)
                .bend(LA, 0, 60, Ease.LINEAR).b(0, -3, 0, Ease.LINEAR)
                .r(RA, 8, -60, -38, 3, Ease.INOUTSINE).r(LA, 8, -60, 38, -3, Ease.INOUTSINE).bend(RA, 8, 65, Ease.INOUTSINE)
                .bend(LA, 8, 65, Ease.INOUTSINE).b(8, -4, 0, Ease.INOUTSINE)
                .r(RA, 16, -55, -35, 0, Ease.INOUTSINE).r(LA, 16, -55, 35, 0, Ease.INOUTSINE).bend(RA, 16, 60, Ease.INOUTSINE)
                .bend(LA, 16, 60, Ease.INOUTSINE).b(16, -3, 0, Ease.INOUTSINE));
        // ---- Suprema carregando: braços abertos, peito para cima, base larga (contínuo)
        add(MahouAnims.CAST_ULT_CHARGE, Kind.SPELL, 3, 6, false, true, false, true, new A(10, 10).loop(0)
                .r(RA, 0, -35, 0, 80, Ease.LINEAR).r(LA, 0, -35, 0, -80, Ease.LINEAR).bend(RA, 0, 15, Ease.LINEAR)
                .bend(LA, 0, 15, Ease.LINEAR).b(0, 10, 0, Ease.LINEAR).r(RL, 0, 0, 0, 10, Ease.LINEAR).r(LL, 0, 0, 0, -10, Ease.LINEAR)
                .r(RA, 5, -37, 0, 74, Ease.INOUTSINE).r(LA, 5, -37, 0, -74, Ease.INOUTSINE).b(5, 11, 0, Ease.INOUTSINE)
                .r(RA, 10, -35, 0, 80, Ease.INOUTSINE).r(LA, 10, -35, 0, -80, Ease.INOUTSINE).b(10, 10, 0, Ease.INOUTSINE));
        // ---- Suprema liberada: junta as mãos e dispara para a frente com tudo (mira)
        add(MahouAnims.CAST_ULT_RELEASE, Kind.SPELL, 3, 2, true, false, true, true, new A(26, 30)
                .r(RA, 0, -35, 0, 80, Ease.LINEAR).r(LA, 0, -35, 0, -80, Ease.LINEAR).bend(RA, 0, 15, Ease.LINEAR)
                .bend(LA, 0, 15, Ease.LINEAR).b(0, 10, 0, Ease.LINEAR).r(RL, 0, 0, 0, 10, Ease.LINEAR).r(LL, 0, 0, 0, -10, Ease.LINEAR)
                .r(RA, 3, -60, -40, 0, Ease.INOUTSINE).r(LA, 3, -60, 40, 0, Ease.INOUTSINE).bend(RA, 3, 70, Ease.INOUTSINE)
                .bend(LA, 3, 70, Ease.INOUTSINE).b(3, -2, 0, Ease.INOUTSINE)
                .r(RA, 6, -100, -10, 5, Ease.OUTBACK).r(LA, 6, -100, 10, -5, Ease.OUTBACK).bend(RA, 6, 0, Ease.OUTCUBIC)
                .bend(LA, 6, 0, Ease.OUTCUBIC).b(6, -12, 0, Ease.OUTCUBIC).r(RL, 6, -30, 0, 6, Ease.OUTCUBIC)
                .r(LL, 6, 25, 0, -6, Ease.OUTCUBIC)
                .r(RA, 16, -96, -10, 5, Ease.LINEAR).r(LA, 16, -96, 10, -5, Ease.LINEAR).b(16, -10, 0, Ease.LINEAR)
                .r(RA, 26, -15, 0, 5, Ease.INOUTSINE).r(LA, 26, -15, 0, -5, Ease.INOUTSINE).b(26, 0, 0, Ease.INOUTSINE)
                .r(RL, 26, 0, 0, 0, Ease.INOUTSINE).r(LL, 26, 0, 0, 0, Ease.INOUTSINE));
    }

    // =====================================================================================
    //  Esquiva
    // =====================================================================================
    private static void dodges() {
        // ---- Frente: cambalhota (o corpo gira 360°; no fim pula de -360 para 0, que é a mesma pose)
        add(MahouAnims.DODGE_F, Kind.DODGE, 4, 1, false, false, false, false, new A(11, 12)
                .b(0, 0, 0, Ease.LINEAR).r(RA, 0, -20, 0, 10, Ease.LINEAR).r(LA, 0, -20, 0, -10, Ease.LINEAR)
                .r(RL, 0, -20, 0, 0, Ease.LINEAR).r(LL, 0, -20, 0, 0, Ease.LINEAR).bend(RA, 0, 0, Ease.LINEAR)
                .bend(LA, 0, 0, Ease.LINEAR)
                .r(RA, 2, -70, 0, 20, Ease.OUTSINE).r(LA, 2, -70, 0, -20, Ease.OUTSINE).bend(RA, 2, 70, Ease.OUTSINE)
                .bend(LA, 2, 70, Ease.OUTSINE).r(RL, 2, -95, 0, 0, Ease.OUTSINE).r(LL, 2, -95, 0, 0, Ease.OUTSINE)
                .b(5, -180, 0, Ease.INSINE)
                .r(RA, 8, -70, 0, 20, Ease.LINEAR).r(LA, 8, -70, 0, -20, Ease.LINEAR).r(RL, 8, -90, 0, 0, Ease.LINEAR)
                .r(LL, 8, -90, 0, 0, Ease.LINEAR)
                .b(9, -360, 0, Ease.OUTSINE)
                .b(10, 0, 0, Ease.CONSTANT)
                .r(RA, 11, -10, 0, 8, Ease.INOUTSINE).r(LA, 11, -10, 0, -8, Ease.INOUTSINE).bend(RA, 11, 0, Ease.INOUTSINE)
                .bend(LA, 11, 0, Ease.INOUTSINE).r(RL, 11, 0, 0, 0, Ease.INOUTSINE).r(LL, 11, 0, 0, 0, Ease.INOUTSINE));
        // ---- Trás: pulinho para trás inclinado, braços à frente para equilibrar
        add(MahouAnims.DODGE_B, Kind.DODGE, 4, 1, false, false, false, false, new A(9, 12)
                .b(0, 0, 0, Ease.LINEAR).y(0, 0, Ease.LINEAR).r(RA, 0, 0, 0, 5, Ease.LINEAR).r(LA, 0, 0, 0, -5, Ease.LINEAR)
                .r(RL, 0, 0, 0, 0, Ease.LINEAR).r(LL, 0, 0, 0, 0, Ease.LINEAR)
                .b(3, 12, 0, Ease.OUTCUBIC).y(3, 0.15F, Ease.OUTCUBIC).r(RA, 3, -50, 0, 35, Ease.OUTCUBIC)
                .r(LA, 3, -50, 0, -35, Ease.OUTCUBIC).r(RL, 3, -35, 0, 5, Ease.OUTCUBIC).r(LL, 3, 10, 0, -5, Ease.OUTCUBIC)
                .b(6, 8, 0, Ease.INSINE).y(6, 0.04F, Ease.INSINE)
                .b(9, 0, 0, Ease.INOUTSINE).y(9, 0, Ease.INOUTSINE).r(RA, 9, 0, 0, 5, Ease.INOUTSINE)
                .r(LA, 9, 0, 0, -5, Ease.INOUTSINE).r(RL, 9, 0, 0, 0, Ease.INOUTSINE).r(LL, 9, 0, 0, 0, Ease.INOUTSINE));
        // ---- Lados: passo lateral baixo, inclinando para o lado do movimento
        add(MahouAnims.DODGE_L, Kind.DODGE, 4, 1, false, false, false, false, side(1));
        add(MahouAnims.DODGE_R, Kind.DODGE, 4, 1, false, false, false, false, side(-1));
    }

    /** Passo lateral: {@code s} = 1 esquerda, -1 direita. */
    private static A side(int s) {
        // Perna "de trás" (a que empurra) abre para o lado contrário; braço contrário abre para equilibrar
        String pushLeg = s > 0 ? RL : LL, leadLeg = s > 0 ? LL : RL;
        String outArm = s > 0 ? RA : LA, inArm = s > 0 ? LA : RA;
        return new A(9, 12)
                .b(0, 0, 0, Ease.LINEAR).y(0, 0, Ease.LINEAR).r(pushLeg, 0, 0, 0, 0, Ease.LINEAR).r(leadLeg, 0, 0, 0, 0, Ease.LINEAR)
                .r(outArm, 0, 0, 0, 0, Ease.LINEAR).r(inArm, 0, 0, 0, 0, Ease.LINEAR)
                .b(2, 0, 18 * s, Ease.OUTCUBIC).y(2, 0.1F, Ease.OUTCUBIC).r(pushLeg, 2, 0, 0, 25 * s, Ease.OUTCUBIC)
                .r(leadLeg, 2, -10, 0, -10 * s, Ease.OUTCUBIC).r(outArm, 2, -20, 0, 45 * s, Ease.OUTCUBIC)
                .r(inArm, 2, -30, 0, -20 * s, Ease.OUTCUBIC)
                .b(6, 0, 10 * s, Ease.INSINE).y(6, 0, Ease.INSINE)
                .b(9, 0, 0, Ease.INOUTSINE).r(pushLeg, 9, 0, 0, 0, Ease.INOUTSINE).r(leadLeg, 9, 0, 0, 0, Ease.INOUTSINE)
                .r(outArm, 9, 0, 0, 0, Ease.INOUTSINE).r(inArm, 9, 0, 0, 0, Ease.INOUTSINE);
    }

    // =====================================================================================
    //  Guarda (escudo levantado): só o braço LIVRE e uma leve inclinação; o braço do escudo fica com a pose do vanilla
    // =====================================================================================
    private static void guards() {
        // Escudo na mão ESQUERDA → braço direito em prontidão
        add(MahouAnims.GUARD_R, Kind.GUARD, 0, 4, false, true, false, false, new A(20, 20).loop(0)
                .r(RA, 0, -55, -15, 15, Ease.LINEAR).bend(RA, 0, 50, Ease.LINEAR).b(0, -4, 0, Ease.LINEAR)
                .r(RA, 10, -58, -15, 15, Ease.INOUTSINE).bend(RA, 10, 55, Ease.INOUTSINE).b(10, -5, 0, Ease.INOUTSINE)
                .r(RA, 20, -55, -15, 15, Ease.INOUTSINE).bend(RA, 20, 50, Ease.INOUTSINE).b(20, -4, 0, Ease.INOUTSINE));
        // Escudo na mão DIREITA → braço esquerdo em prontidão
        add(MahouAnims.GUARD_L, Kind.GUARD, 0, 4, false, true, false, false, new A(20, 20).loop(0)
                .r(LA, 0, -55, 15, -15, Ease.LINEAR).bend(LA, 0, 50, Ease.LINEAR).b(0, -4, 0, Ease.LINEAR)
                .r(LA, 10, -58, 15, -15, Ease.INOUTSINE).bend(LA, 10, 55, Ease.INOUTSINE).b(10, -5, 0, Ease.INOUTSINE)
                .r(LA, 20, -55, 15, -15, Ease.INOUTSINE).bend(LA, 20, 50, Ease.INOUTSINE).b(20, -4, 0, Ease.INOUTSINE));
    }

    private static void add(String id, Kind kind, int rank, int fadeIn, boolean aim, boolean hold, boolean faceLook, boolean mirror,
                            A a) {
        try {
            DEFS.put(id, new Def(id, a.build(id), kind, rank, fadeIn, aim, hold, faceLook, mirror));
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.error("[anim] animação inválida: {}", id, e);
        }
    }

    /** Construtor enxuto de keyframes (valores em graus; posição do corpo em blocos). */
    private static final class A {
        private final KeyframeAnimation.AnimationBuilder b = new KeyframeAnimation.AnimationBuilder(AnimationFormat.JSON_EMOTECRAFT);

        A(int end, int stop) {
            b.beginTick = 0;
            b.endTick = end;
            b.stopTick = Math.max(end, stop);
            b.isEasingBefore = true;
            b.isLooped = false;
            b.returnTick = 0;
        }

        A loop(int returnTick) {
            b.isLooped = true;
            b.returnTick = returnTick;
            return this;
        }

        /** Rotação (pitch, yaw, roll) de uma parte. */
        A r(String part, int t, float pitch, float yaw, float roll, Ease e) {
            KeyframeAnimation.StateCollection p = b.getPart(part);
            p.pitch.addKeyFrame(t, pitch, e, 0, true);
            p.yaw.addKeyFrame(t, yaw, e, 0, true);
            p.roll.addKeyFrame(t, roll, e, 0, true);
            return this;
        }

        /** Dobra (cotovelo/joelho). */
        A bend(String part, int t, float deg, Ease e) {
            KeyframeAnimation.StateCollection p = b.getPart(part);
            if (p.isBendable() && p.bend != null) p.bend.addKeyFrame(t, deg, e, 0, true);
            return this;
        }

        /** Torção do tronco (yaw). */
        A tw(int t, float yaw, Ease e) {
            return r(T, t, 0, yaw, 0, e);
        }

        /** Jogador inteiro: pitch (− = para a frente) e roll (+ = para a esquerda). */
        A b(int t, float pitch, float roll, Ease e) {
            return r(B, t, pitch, 0, roll, e);
        }

        /** Jogador inteiro: altura (blocos). */
        A y(int t, float blocks, Ease e) {
            b.getPart(B).y.addKeyFrame(t, blocks, e);
            return this;
        }

        KeyframeAnimation build(String name) {
            b.name = name;
            return b.build();
        }
    }

    private AnimLibrary() {}
}
