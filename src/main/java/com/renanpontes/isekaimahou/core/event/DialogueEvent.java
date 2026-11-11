package com.renanpontes.isekaimahou.core.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * Eventos da caixa de diálogo do núcleo ({@link com.renanpontes.isekaimahou.core.api.Dialogue}), postados em
 * {@code NeoForge.EVENT_BUS} no servidor.
 */
public abstract class DialogueEvent extends Event {
    private final ServerPlayer player;
    private final String dialogueId;

    protected DialogueEvent(ServerPlayer player, String dialogueId) {
        this.player = player;
        this.dialogueId = dialogueId;
    }

    public ServerPlayer getPlayer() { return player; }

    /** Id passado em {@code Dialogue.show} (ex.: {@code "journal.tutorial"}). */
    public String getDialogueId() { return dialogueId; }

    /**
     * O jogador terminou o diálogo: leu a última página (e cumpriu a ação dela, se havia) ou pulou tudo
     * ({@link #isSkipped()}). NÃO é postado quando o servidor fecha o diálogo com {@code Dialogue.close} nem quando o
     * jogador sai do mundo com o diálogo aberto.
     */
    public static class Finished extends DialogueEvent {
        private final boolean skipped;

        public Finished(ServerPlayer player, String dialogueId, boolean skipped) {
            super(player, dialogueId);
            this.skipped = skipped;
        }

        /** true = o jogador pulou (Backspace) antes do fim. */
        public boolean isSkipped() { return skipped; }
    }
}
