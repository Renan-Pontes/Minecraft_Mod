package com.renanpontes.isekaimahou.feature.journal.story;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Dialogue;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.event.DialogueEvent;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.journal.JournalService;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * A campanha no servidor: escuta os marcos (capítulo aberto/concluído e passo cumprido vêm do {@link JournalService};
 * contadores e Círculos, dos eventos do núcleo), põe a batida na fila do jogador e a entrega quando der.
 * <ul>
 *   <li><b>Nunca prende:</b> a fala é a caixa de diálogo do núcleo (não tira o controle; Backspace pula) e a cutscene de
 *       marco não trava as teclas (Enter pula; atacar ou levar dano devolve a câmera).</li>
 *   <li><b>Pulou, pulou:</b> pulada, ou com a campanha desligada, a batida só fica marcada como passada — dá para ler no
 *       Diário ("Ecos" do capítulo). Pular o primeiro trecho de uma fala fecha os outros.</li>
 *   <li><b>Uma coisa de cada vez:</b> espera o tutorial, outros diálogos, o combate e a cena de morte dos chefes (a
 *       câmera do abate de 2 s), com pelo menos 15 s entre uma batida e outra. Progresso em lote (mundo antigo,
 *       comando) não despeja falas: além de {@link #MAX_QUEUE} na fila, o resto vai direto para o Diário.</li>
 *   <li><b>Nada retroativo:</b> só marcos que acontecem AGORA disparam; o que já passou antes desta versão aparece no
 *       Diário quando o capítulo está concluído.</li>
 * </ul>
 */
public final class StoryService {
    /** Batidas esperando ao mesmo tempo; acima disso vão direto para o Diário. */
    private static final int MAX_QUEUE = 3;
    /** Intervalo mínimo entre duas batidas. */
    private static final int GAP = 15 * 20;
    /** Respiro (ticks) depois que o tutorial, outra fala ou uma luta terminam. */
    private static final int SETTLE = 80;
    /** Sem levar golpe há este tempo (ticks) para a fala começar. */
    private static final int CALM = 100;
    /** Duração da cutscene de marco (6,5 s: título + uma frase). */
    private static final int SCENE_TICKS = 130;
    /** Fila refeita no login: a fala vem um pouco depois de o jogador entrar. */
    private static final int LOGIN_DELAY = 200;

    /** O que o jogador escolheu nas opções (vem do cliente; sem resposta ainda = tudo ligado). */
    public record Prefs(boolean campaign, boolean scenes) {
        static final Prefs DEFAULT = new Prefs(true, true);
    }

    private record Pending(StoryBook.Beat beat, long readyAt) {}

    private static final Map<UUID, Prefs> PREFS = new HashMap<>();
    private static final Map<UUID, Deque<Pending>> QUEUE = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((MagicProgressEvent.Counter e) -> onCounter(e.getPlayer(), e.key, e.value));
        bus.addListener((MagicProgressEvent.CircleChanged e) -> {
            if (e.newCircle > e.oldCircle) onCircle(e.getPlayer(), e.newCircle);
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) restore(sp);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity().getUUID()));
        bus.addListener(StoryService::onDialogueFinished);
    }

    // =====================================================================================
    //  Opções do jogador
    // =====================================================================================
    public static Prefs prefs(ServerPlayer player) {
        return PREFS.getOrDefault(player.getUUID(), Prefs.DEFAULT);
    }

    /** O cliente avisou as opções (no login e quando mudam). Desligar a campanha esvazia a fila (vai tudo para o Diário). */
    public static void setPrefs(ServerPlayer player, boolean campaign, boolean scenes) {
        PREFS.put(player.getUUID(), new Prefs(campaign, scenes));
        if (campaign) return;
        Deque<Pending> q = QUEUE.remove(player.getUUID());
        if (q == null) return;
        MagicData d = MagicApi.get(player);
        for (Pending p : q) pass(d, p.beat());
    }

    // =====================================================================================
    //  Marcos
    // =====================================================================================
    public static void onChapterBegin(ServerPlayer player, String chapter) {
        fire(player, StoryBook.Trigger.CHAPTER_BEGIN, chapter);
    }

    public static void onChapterDone(ServerPlayer player, String chapter) {
        fire(player, StoryBook.Trigger.CHAPTER_DONE, chapter);
    }

    /** {@code step}: "capitulo.passo". */
    public static void onStep(ServerPlayer player, String step) {
        fire(player, StoryBook.Trigger.STEP, step);
    }

    private static void fire(ServerPlayer player, StoryBook.Trigger trigger, String key) {
        for (StoryBook.Beat b : StoryBook.beats()) {
            if (b.trigger() == trigger && b.key().equals(key)) enqueue(player, b);
        }
    }

    private static void onCounter(ServerPlayer player, String key, int value) {
        for (StoryBook.Beat b : StoryBook.beats()) {
            if (value >= b.min() && b.matchesCounter(key)) enqueue(player, b);
        }
    }

    private static void onCircle(ServerPlayer player, int newCircle) {
        for (StoryBook.Beat b : StoryBook.beats()) {
            // Só o Círculo que acabou de ser rompido (um salto por comando não despeja as falas dos anteriores).
            if (b.trigger() == StoryBook.Trigger.CIRCLE && b.min() == newCircle) {
                enqueue(player, b);
            }
        }
    }

    /** Põe a batida na fila (uma vez por personagem). Campanha desligada ou fila cheia: vai direto para o Diário. */
    private static void enqueue(ServerPlayer player, StoryBook.Beat beat) {
        if (player instanceof FakePlayer) return;
        MagicData d = MagicApi.get(player);
        if (d.hasFlag(beat.seenFlag()) || d.hasFlag(beat.queuedFlag())) return;
        Deque<Pending> q = QUEUE.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        if (!prefs(player).campaign() || q.size() >= MAX_QUEUE) {
            pass(d, beat);
            if (q.isEmpty()) QUEUE.remove(player.getUUID());
            return;
        }
        d.setFlag(beat.queuedFlag(), true);
        q.add(new Pending(beat, now(player) + beat.delay()));
    }

    /** Marca como passada sem mostrar. */
    private static void pass(MagicData d, StoryBook.Beat beat) {
        d.setFlag(beat.queuedFlag(), false);
        d.setFlag(beat.seenFlag(), true);
    }

    /** Login: refaz a fila com o que tinha ficado pendente quando o jogador saiu. */
    private static void restore(ServerPlayer player) {
        if (player instanceof FakePlayer) return;
        MagicData d = MagicApi.get(player);
        Deque<Pending> q = null;
        for (StoryBook.Beat b : StoryBook.beats()) {
            if (!d.hasFlag(b.queuedFlag())) continue;
            if (d.hasFlag(b.seenFlag())) {
                d.setFlag(b.queuedFlag(), false);
                continue;
            }
            if (q == null) q = QUEUE.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
            if (q.stream().noneMatch(p -> p.beat() == b)) q.add(new Pending(b, now(player) + LOGIN_DELAY));
        }
    }

    // =====================================================================================
    //  Entrega
    // =====================================================================================
    /** Chamado a cada tick do jogador pelo Diário: confere a fila uma vez por segundo. */
    public static void tick(ServerPlayer player) {
        if (player.tickCount % 20 != 7) return;
        Deque<Pending> q = QUEUE.get(player.getUUID());
        if (q == null) return;
        if (q.isEmpty()) {
            QUEUE.remove(player.getUUID());
            return;
        }
        Pending next = q.peekFirst();
        long now = now(player);
        if (!calm(player)) {
            // Tutorial, outra fala ou luta em andamento: depois que acabar, um respiro antes de o Eco começar.
            LAST.merge(player.getUUID(), now + SETTLE - GAP, Math::max);
            return;
        }
        if (now < next.readyAt() || now < LAST.getOrDefault(player.getUUID(), 0L) + GAP) return;
        q.pollFirst();
        if (q.isEmpty()) QUEUE.remove(player.getUUID());
        LAST.put(player.getUUID(), now);
        deliver(player, next.beat());
    }

    /** Dá para falar agora? Vivo, fora do tutorial, sem outro diálogo na tela e sem apanhar. */
    private static boolean calm(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || player.isSleeping()) return false;
        if (Onboarding.quiet(player) || Dialogue.isBusy(player)) return false;
        return player.getLastHurtByMob() == null || player.tickCount - player.getLastHurtByMobTimestamp() > CALM;
    }

    /** Mostra a batida agora (também usado pelo comando de teste, que ignora a fila e os marcadores). */
    public static void deliver(ServerPlayer player, StoryBook.Beat beat) {
        MagicData d = MagicApi.get(player);
        pass(d, beat);
        boolean scene = beat.scene() && prefs(player).scenes();
        if (scene) {
            // Cutscene de marco: não trava as teclas; o cliente a segura atrás de uma cena de chefe em andamento.
            Cutscenes.play(player, "story", player, beat.title(), beat.narration(), beat.rgb(), SCENE_TICKS, false);
            if (beat.lines() > 0) JournalService.later(player, SCENE_TICKS - 10, p -> talk(p, beat, false));
        } else {
            talk(player, beat, beat.scene());
        }
    }

    /**
     * As falas: um diálogo por trecho (cada um com o nome de quem fala), na fila do núcleo. Sem cutscene, a narração da
     * cena abre a conversa na voz da Crônica.
     */
    private static void talk(ServerPlayer player, StoryBook.Beat beat, boolean narrate) {
        if (!player.isAlive()) return;
        boolean echo = false;
        if (narrate) {
            Dialogue.show(player, dialogueId(beat, 0), StoryBook.speaker("narrator"), beat.rgb(),
                    List.of(Dialogue.Page.of(beat.narration().withStyle(ChatFormatting.ITALIC))));
        }
        int line = 1;
        for (int i = 0; i < beat.parts().size(); i++) {
            StoryBook.Part part = beat.parts().get(i);
            List<Dialogue.Page> pages = new ArrayList<>(part.lines());
            for (int k = 0; k < part.lines(); k++) pages.add(Dialogue.Page.of(beat.line(line++)));
            boolean isEcho = part.speaker().equals("echo");
            echo |= isEcho;
            Dialogue.show(player, dialogueId(beat, i + 1), StoryBook.speaker(part.speaker()),
                    isEcho ? StoryBook.ECHO : beat.rgb(), pages);
        }
        if (echo) {
            // O Eco é o Espírito Guia: ele surge ao lado para falar e se despede quando a conversa acaba.
            JournalService.summonGuide(player, null, false);
            JournalService.dismissLater(player);
        } else {
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.7F, 0.7F);
        }
    }

    private static String dialogueId(StoryBook.Beat beat, int part) {
        return "story." + beat.id() + "." + part;
    }

    /** Alguma fala da campanha aberta ou na fila de diálogos? (o Espírito Guia espera para ir embora) */
    public static boolean talking(ServerPlayer player) {
        for (StoryBook.Beat b : StoryBook.beats()) {
            for (int i = 0; i <= b.parts().size(); i++) {
                if (Dialogue.isOpen(player, dialogueId(b, i))) return true;
            }
        }
        return false;
    }

    /** Pulou um trecho (Backspace): os outros trechos da mesma batida não insistem. */
    private static void onDialogueFinished(DialogueEvent.Finished event) {
        String id = event.getDialogueId();
        if (!event.isSkipped() || id == null || !id.startsWith("story.")) return;
        int dot = id.lastIndexOf('.');
        if (dot <= 6) return;
        StoryBook.Beat beat = StoryBook.byId(id.substring(6, dot));
        if (beat == null) return;
        for (int i = 0; i <= beat.parts().size(); i++) Dialogue.close(event.getPlayer(), dialogueId(beat, i));
    }

    // =====================================================================================
    //  Comandos de teste e limpeza
    // =====================================================================================
    /** Quantas batidas este personagem já passou (vistas, puladas ou guardadas). */
    public static int seen(MagicData d) {
        int n = 0;
        for (StoryBook.Beat b : StoryBook.beats()) if (d.hasFlag(b.seenFlag())) n++;
        return n;
    }

    public static int queued(ServerPlayer player) {
        Deque<Pending> q = QUEUE.get(player.getUUID());
        return q == null ? 0 : q.size();
    }

    /** Apaga os marcadores da campanha (as batidas voltam a disparar nos próximos marcos). */
    public static int reset(ServerPlayer player) {
        MagicData d = MagicApi.get(player);
        List<String> flags = d.flags().stream().filter(f -> f.startsWith("story.")).toList();
        flags.forEach(f -> d.setFlag(f, false));
        QUEUE.remove(player.getUUID());
        LAST.remove(player.getUUID());
        return flags.size();
    }

    private static void forget(UUID id) {
        PREFS.remove(id);
        QUEUE.remove(id);
        LAST.remove(id);
    }

    public static void clearAll() {
        PREFS.clear();
        QUEUE.clear();
        LAST.clear();
    }

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    private StoryService() {}
}
