package com.renanpontes.isekaimahou.core.api;

import java.util.List;

import com.renanpontes.isekaimahou.core.onboard.OnboardService;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Entrada no jogo (v0.9): escolher a classe → "Parabéns!" → (o Diário) título do capítulo 1 → Espírito Guia ensina
 * J, B, Grimório e conjuração → {@link #finishTutorial}. Enquanto o jogador está nessa sequência os módulos devem ficar
 * SILENCIOSOS (sem chat, toasts ou letreiros próprios): consulte {@link #quiet(Player)}.
 *
 * <p>Flags em {@code MagicData}: {@link #FLAG_CLASS} (classe escolhida pela tela), {@link #FLAG_CONGRATS} (letreiro
 * mostrado / {@code ClassCongratsDone} postado), {@link #FLAG_TUTORIAL} (tutorial terminado ou pulado). Personagens
 * de versões antigas (classe escolhida sem {@link #FLAG_CLASS}) contam como tutorial terminado.
 * Eventos: {@code core.event.OnboardingEvent}. Comando op: {@code /mahou onboarding reset|skip [jogador]}.
 */
public final class Onboarding {
    public static final String FLAG_CLASS = "onboard.class";
    public static final String FLAG_CONGRATS = "onboard.congrats";
    public static final String FLAG_TUTORIAL = "onboard.tutorial";
    /** Depois de quanto tempo da escolha da classe o silêncio acaba mesmo sem o tutorial (5 min). */
    public static final int QUIET_TICKS = 5 * 60 * 20;

    /**
     * true enquanto o jogador está entrando no jogo: sem classe, montando o personagem (etapas de criação), ou classe escolhida há menos de 5 min e tutorial
     * não terminado. Módulos que mandam chat/toast/letreiro ao escolher a classe (ou nos primeiros minutos) devem
     * pular a mensagem quando for true. Servidor (num jogador do cliente devolve sempre false).
     */
    public static boolean quiet(Player p) {
        return OnboardService.quiet(p);
    }

    /** A classe foi escolhida pela tela de entrada da v0.9+ (flag {@link #FLAG_CLASS}). */
    public static boolean classChosen(Player p) {
        return OnboardService.classChosen(p);
    }

    /** O letreiro de parabéns já terminou ({@code ClassCongratsDone} já postado). */
    public static boolean congratsDone(Player p) {
        return OnboardService.congratsDone(p);
    }

    /** Tutorial terminado (ou pulado, ou personagem de versão antiga). */
    public static boolean tutorialDone(Player p) {
        return OnboardService.tutorialDone(p);
    }

    /**
     * Conclui o tutorial: grava {@link #FLAG_TUTORIAL}, posta {@code OnboardingEvent.TutorialDone(p, false)} e libera
     * os avisos guardados no cliente. Chamar de novo não faz nada.
     */
    public static void finishTutorial(ServerPlayer p) {
        OnboardService.finishTutorial(p, false);
    }

    /**
     * Pula o tutorial (mesmo que {@link #finishTutorial}, com {@code skipped = true}). Não fecha diálogos (o comando
     * {@code /mahou onboarding skip} fecha). Se o letreiro ainda não tinha acabado, {@code ClassCongratsDone} é postado
     * antes (com {@link #tutorialDone} já true: o Diário começa a história sem ensinar).
     */
    public static void skipTutorial(ServerPlayer p) {
        OnboardService.finishTutorial(p, true);
    }

    // =====================================================================================
    //  Etapas de criação de personagem (§3.2b)
    // =====================================================================================

    /**
     * Registra uma etapa de criação de personagem (chame no init do módulo, nos dois lados). Depois da escolha da classe
     * as etapas abrem uma por vez, na ordem de registro ({@code OnboardingEvent.CreationStep} — o módulo abre a sua
     * tela), e o "Parabéns!" só aparece quando todas forem concluídas ({@link #completeCreationStep}). Ids repetidos
     * são ignorados. Flag de etapa concluída: {@code onboard.step.<id>}.
     */
    public static void addCreationStep(String id) {
        OnboardService.addCreationStep(id);
    }

    /** Etapas registradas, na ordem (só leitura). */
    public static List<String> creationSteps() {
        return OnboardService.creationSteps();
    }

    /**
     * Etapas que o jogador ainda precisa fazer, na ordem. Vazia depois do "Parabéns!" (ou para personagens de versões
     * antigas, que não passam pelas etapas a menos que um op use {@code /mahou onboarding reset}). Funciona nos dois
     * lados (no cliente, com os dados sincronizados do jogador local).
     */
    public static List<String> pendingCreationSteps(Player p) {
        return OnboardService.pendingCreationSteps(p);
    }

    /**
     * Conclui a etapa {@code id} (o módulo chama quando o jogador confirma a tela dele): a próxima etapa abre, ou, se
     * era a última, o letreiro "Parabéns!" aparece e a entrada segue. Etapa desconhecida ou já concluída: nada acontece.
     */
    public static void completeCreationStep(ServerPlayer p, String id) {
        OnboardService.completeCreationStep(p, id);
    }

    private Onboarding() {}
}
