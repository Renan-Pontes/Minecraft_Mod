package com.renanpontes.isekaimahou.core.api;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.onboard.DialogueService;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Caixa de diálogo do núcleo (v0.9): fala de um personagem na parte de baixo da tela, página por página, com o texto
 * aparecendo letra a letra. Não pausa o jogo nem tira o controle. O jogador avança com {@code Enter} (tecla
 * {@code key.isekai_mahou.dialogue_next}), clicando na caixa com o chat aberto ou esperando; segura {@code Backspace}
 * para pular o diálogo inteiro. Um diálogo por vez: os outros esperam numa fila.
 *
 * <p>Páginas com {@link Page#await() await} mostram a ação pedida com o nome REAL da tecla configurada ("Aperte [J]")
 * e só avançam quando o jogador a faz. Valores de {@code await}:
 * <ul>
 *   <li>{@link #AWAIT_TRACKER} {@code "key:tracker"} — apertar a tecla do rastreador (J); o rastreador fica visível</li>
 *   <li>{@link #AWAIT_MENU} {@code "key:menu"} — abrir o menu (B)</li>
 *   <li>{@link #AWAIT_WHEEL} {@code "key:wheel"} — trocar o elemento da Roda (Mouse 5 + rolar)</li>
 *   <li>{@code "key:<tecla>"} — outra tecla: {@code slot1..slot3}, {@code ultimate}, {@code loadout_modifier},
 *       {@code dialogue_next}, ou o nome completo de qualquer tecla ({@code "key:key.inventory"})</li>
 *   <li>{@link #AWAIT_GRIMOIRE} {@code "screen:grimoire"} — abrir a aba Grimório do menu; {@code "screen:<aba>"} vale
 *       para qualquer aba do menu B (ids de {@code MenuTabs})</li>
 *   <li>{@link #AWAIT_CAST} {@code "cast"} — conjurar qualquer magia (o servidor confirma)</li>
 *   <li>{@code "signal:<token>"} — ação própria de um módulo: ele chama {@link #signal(ServerPlayer, String)} com o
 *       mesmo {@code token} quando o jogador a fizer</li>
 * </ul>
 * Ao terminar (ou pular), o servidor posta {@code DialogueEvent.Finished(player, id, skipped)}.
 *
 * <pre>
 * Dialogue.show(sp, "journal.tutorial", Component.translatable("entity.isekai_mahou.guide_spirit"), 0x9FE8FF, List.of(
 *         Dialogue.Page.of(Component.translatable("journal.isekai_mahou.tutorial.1")),
 *         Dialogue.Page.await(Component.translatable("journal.isekai_mahou.tutorial.j"), Dialogue.AWAIT_TRACKER)));
 * </pre>
 */
public final class Dialogue {
    public static final String AWAIT_TRACKER = "key:tracker";
    public static final String AWAIT_MENU = "key:menu";
    public static final String AWAIT_WHEEL = "key:wheel";
    public static final String AWAIT_GRIMOIRE = "screen:grimoire";
    public static final String AWAIT_CAST = "cast";
    /** Máximo de páginas por diálogo (o resto é ignorado). */
    public static final int MAX_PAGES = 64;

    /**
     * Uma página.
     *
     * @param text  o que o personagem fala (até ~4 linhas na caixa; textos maiores são cortados com "…")
     * @param await ação que o jogador precisa fazer para avançar, ou null (avança com Enter/clique/tempo)
     */
    public record Page(Component text, @Nullable String await) {
        public static Page of(Component text) {
            return new Page(text, null);
        }

        public static Page await(Component text, String await) {
            return new Page(text, await);
        }
    }

    /**
     * Mostra (ou põe na fila) um diálogo para o jogador. Mesmo {@code dialogueId} já aberto/na fila: é substituído.
     *
     * @param dialogueId id livre do chamador, devolvido no evento de fim (ex.: {@code "journal.tutorial"})
     * @param speaker    nome de quem fala (aparece na aba da caixa, na cor {@code rgb})
     * @param rgb        cor do falante (RGB)
     * @param pages      páginas (vazia = nada acontece)
     */
    public static void show(ServerPlayer p, String dialogueId, Component speaker, int rgb, List<Page> pages) {
        DialogueService.show(p, dialogueId, speaker, rgb, pages);
    }

    /** Fecha o diálogo atual e esvazia a fila do jogador (sem evento de fim). */
    public static void close(ServerPlayer p) {
        DialogueService.close(p, null);
    }

    /** Fecha um diálogo específico (aberto ou na fila), sem evento de fim. */
    public static void close(ServerPlayer p, String dialogueId) {
        DialogueService.close(p, dialogueId);
    }

    /** O diálogo está aberto ou na fila deste jogador? */
    public static boolean isOpen(ServerPlayer p, String dialogueId) {
        return DialogueService.isOpen(p, dialogueId);
    }

    /** O jogador tem algum diálogo aberto ou na fila? */
    public static boolean isBusy(ServerPlayer p) {
        return DialogueService.isBusy(p);
    }

    /**
     * Avisa o cliente que o jogador fez a ação {@code token}: uma página com {@code await = "signal:<token>"} (ou
     * {@code "cast"} para {@code token = "cast"}) avança. Sem diálogo aberto, não faz nada.
     */
    public static void signal(ServerPlayer p, String token) {
        DialogueService.signal(p, token);
    }

    private Dialogue() {}
}
