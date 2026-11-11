package com.renanpontes.isekaimahou.feature.journal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Dialogue;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.event.DialogueEvent;
import com.renanpontes.isekaimahou.feature.journal.data.JournalBook;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Tutorial da entrada no jogo (v0.9), contado pelo Espírito Guia na caixa de diálogo do núcleo ({@link Dialogue}):
 * <ol>
 *   <li>a história em 3 falas (o isekai, o Dragão Primordial selado, a classe escolhida);</li>
 *   <li>"Aperte J" — espera a tecla do rastreador (o rastreador de objetivos aparece);</li>
 *   <li>"Abra o Menu com B" — espera o menu;</li>
 *   <li>"Clique na aba Grimório" — espera a aba;</li>
 *   <li>"Conjure com Z" — texto por categoria (Mágicas / Físicas / Destreza), espera a conjuração;</li>
 *   <li>v1.0: os 9 loadouts (Alt + rolagem ou Mouse 5 + rolagem) e a ROTA DA CLASSE no Diário (espera abrir a aba);</li>
 *   <li>despedida → {@code Onboarding.finishTutorial} (ou {@code skipTutorial} se o jogador pulou com Backspace).</li>
 * </ol>
 * Retomada ({@code OnboardingEvent.TutorialResume}): só as etapas que ainda faltam (os marcadores do tutorial do Diário
 * dizem se o menu e o Grimório já foram abertos; os contadores {@code cast.*}, se já conjurou).
 */
public final class JournalTutorial {
    /** Id do diálogo (o fim dele conclui o tutorial). */
    public static final String ID = "journal.tutorial";
    private static final String KEY = "journal.isekai_mahou.tutorial.";
    /** A história (3 falas) já foi contada: na retomada ela não se repete. */
    private static final String STORY_FLAG = "journal.tutorial.story";
    /** Os loadouts já foram explicados (a retomada não repete). */
    private static final String LOADOUT_FLAG = "journal.tutorial.loadouts";
    /** Espera o jogador abrir a aba Diário do menu B (id da aba em {@code MenuTabs}). */
    private static final String JOURNAL_TAB = "screen:journal";

    /** Jogadores com o tutorial agendado (evita agendar duas vezes; limpo ao sair). */
    private static final Set<UUID> SCHEDULED = new HashSet<>();

    /**
     * Agenda o tutorial para daqui a {@code delay} ticks (uma vez). Não faz nada se o tutorial já terminou, se não há
     * classe ou se ele já está aberto.
     */
    static void schedule(ServerPlayer player, int delay, boolean resume) {
        if (player instanceof FakePlayer || Onboarding.tutorialDone(player) || MagicApi.get(player).path() == MagicPath.NONE) return;
        if (Dialogue.isOpen(player, ID) || !SCHEDULED.add(player.getUUID())) return;
        JournalService.later(player, delay, p -> start(p, resume));
    }

    /** O tutorial está aberto (ou na fila) para este jogador? */
    static boolean running(ServerPlayer player) {
        return SCHEDULED.contains(player.getUUID()) || Dialogue.isOpen(player, ID);
    }

    /** Começa agora (comando de teste ou agenda). */
    static void start(ServerPlayer player, boolean resume) {
        SCHEDULED.remove(player.getUUID());
        if (Onboarding.tutorialDone(player) || MagicApi.get(player).path() == MagicPath.NONE) return;
        if (!player.isAlive() || player.isSpectator()) {
            // Morto ou espectador: tenta de novo daqui a pouco (o espírito não pode aparecer na tela de morte).
            schedule(player, 40, resume);
            return;
        }
        open(player, resume);
    }

    /** Abre o diálogo (o espírito surge ao lado) — também usado pelo comando de teste, mesmo com o tutorial concluído. */
    static void open(ServerPlayer player, boolean resume) {
        JournalBook book = JournalBook.server();
        JournalService.summonGuide(player, book.isEmpty() ? null : book.chapters().get(0), false);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.9F, 1.4F);
        JournalService.spiritSays(player, ID, JournalService.SPIRIT_RGB, pages(player, resume));
    }

    /** Páginas do tutorial (na retomada, só o que falta). */
    static List<Dialogue.Page> pages(ServerPlayer player, boolean resume) {
        MagicData d = MagicApi.get(player);
        MagicPath path = d.path();
        List<Dialogue.Page> out = new ArrayList<>(9);
        if (resume && d.hasFlag(STORY_FLAG)) {
            out.add(Dialogue.Page.of(text("resume")));
        } else {
            // Primeira vez (ou saiu do mundo antes de o espírito começar a falar): a história inteira.
            d.setFlag(STORY_FLAG, true);
            out.add(Dialogue.Page.of(text("story1")));
            out.add(Dialogue.Page.of(text("story2")));
            out.add(Dialogue.Page.of(text("story3", path.displayName().withStyle(s -> s.withColor(path.rgb()).withBold(true)))));
        }
        out.add(Dialogue.Page.await(text("press_j", key("tracker")), Dialogue.AWAIT_TRACKER));
        if (!resume || !d.hasFlag("journal.tutorial.menu")) {
            out.add(Dialogue.Page.await(text("press_b", key("menu")), Dialogue.AWAIT_MENU));
        }
        if (!resume || !d.hasFlag("journal.tutorial.grimoire")) {
            out.add(Dialogue.Page.await(text("grimoire"), Dialogue.AWAIT_GRIMOIRE));
        }
        if (!resume || !hasCast(d)) {
            Component resource = MagicApi.resourceName(player).withStyle(s -> s.withColor(MagicApi.resourceColor(player) & 0xFFFFFF));
            out.add(Dialogue.Page.await(text("cast." + path.category().id, key("slot1"), resource), Dialogue.AWAIT_CAST));
        }
        if (!resume || !d.hasFlag(LOADOUT_FLAG)) {
            d.setFlag(LOADOUT_FLAG, true);
            out.add(Dialogue.Page.of(text("loadouts", key("loadout_modifier"), key("loadout_mouse"))));
        }
        // Rota da classe: só quando o livro tem capítulos para a classe do jogador.
        if (!JournalBook.server().classTrack(path).isEmpty()) {
            out.add(Dialogue.Page.await(text("class_route", path.displayName().withStyle(s -> s.withColor(path.rgb()).withBold(true)),
                    key("menu")), JOURNAL_TAB));
        }
        out.add(Dialogue.Page.of(text("end")));
        return out;
    }

    private static boolean hasCast(MagicData d) {
        for (Map.Entry<String, Integer> e : d.counters().entrySet()) {
            if (e.getKey().startsWith("cast.") && e.getValue() > 0) return true;
        }
        return false;
    }

    /**
     * Fim do diálogo do tutorial: conclui (ou pula) o tutorial do núcleo e o espírito se despede logo depois.
     * Quem pulou antes de aprender o J ganha o lembrete na barra de ação (o rastreador começa fechado).
     */
    static void onFinished(DialogueEvent.Finished event) {
        if (!ID.equals(event.getDialogueId())) return;
        ServerPlayer player = event.getPlayer();
        SCHEDULED.remove(player.getUUID());
        if (event.isSkipped()) {
            Onboarding.skipTutorial(player);
            player.displayClientMessage(Component.translatable("message.isekai_mahou.journal.tutorial_skipped",
                    key("tracker")).withStyle(ChatFormatting.AQUA), true);
        } else {
            Onboarding.finishTutorial(player);
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.8F);
        }
        JournalService.markDirty(player);
        JournalService.later(player, 60, JournalService::dismissGuide);
    }

    private static MutableComponent text(String id, Object... args) {
        return Component.translatable(KEY + id, args);
    }

    /** Nome REAL da tecla configurada (resolvido no cliente), em destaque. */
    private static MutableComponent key(String name) {
        return Component.keybind("key.isekai_mahou." + name).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
    }

    static void forget(UUID id) {
        SCHEDULED.remove(id);
    }

    static void clearAll() {
        SCHEDULED.clear();
    }

    private JournalTutorial() {}
}
