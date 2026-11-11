package com.renanpontes.isekaimahou.core.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Etapas da entrada no jogo ({@link com.renanpontes.isekaimahou.core.api.Onboarding}), postadas em
 * {@code NeoForge.EVENT_BUS} no servidor. Ordem: escolha da classe ({@code MagicProgressEvent.PathChosen}) →
 * letreiro "Parabéns!" no cliente → {@link ClassCongratsDone} (~4 s depois) → tutorial do Diário → {@link TutorialDone}.
 */
public abstract class OnboardingEvent extends Event {
    private final ServerPlayer player;

    protected OnboardingEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() { return player; }

    /**
     * Uma etapa de criação de personagem deve abrir agora ({@code Onboarding.addCreationStep}): o módulo dono de
     * {@link #getStepId()} abre a sua tela e, quando o jogador confirmar, chama {@code Onboarding.completeCreationStep}.
     * Postado depois da escolha da classe (uma etapa por vez, na ordem de registro) e de novo ~3 s depois de entrar no
     * mundo se a etapa ainda estiver pendente (o jogador saiu no meio).
     */
    public static class CreationStep extends OnboardingEvent {
        private final String stepId;

        public CreationStep(ServerPlayer player, String stepId) {
            super(player);
            this.stepId = stepId;
        }

        public String getStepId() { return stepId; }
    }

    /**
     * O letreiro de "Parabéns!" da classe acabou (~4 s depois da escolha; se o jogador saiu antes, é postado ~3 s
     * depois da próxima entrada). Uma vez por personagem (flag {@code onboard.congrats}). O Diário começa o capítulo 1
     * aqui e chama o Espírito Guia.
     */
    public static class ClassCongratsDone extends OnboardingEvent {
        public ClassCongratsDone(ServerPlayer player) { super(player); }
    }

    /**
     * O jogador entrou no mundo com o tutorial começado e não terminado (letreiro já mostrado, flag
     * {@code onboard.tutorial} ausente). Quem roda o tutorial reabre o diálogo a partir daqui (~3 s depois da entrada).
     */
    public static class TutorialResume extends OnboardingEvent {
        public TutorialResume(ServerPlayer player) { super(player); }
    }

    /**
     * Tutorial concluído ({@code Onboarding.finishTutorial}) ou pulado ({@link #isSkipped()}, ex. comando
     * {@code /mahou onboarding skip}). Uma vez por personagem (flag {@code onboard.tutorial}). Depois disso
     * {@code Onboarding.quiet} é false e os avisos guardados (teclas trocadas) aparecem.
     */
    public static class TutorialDone extends OnboardingEvent {
        private final boolean skipped;

        public TutorialDone(ServerPlayer player, boolean skipped) {
            super(player);
            this.skipped = skipped;
        }

        public boolean isSkipped() { return skipped; }
    }
}
