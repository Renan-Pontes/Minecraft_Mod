package com.renanpontes.isekaimahou.feature.journal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Dialogue;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.feature.journal.data.JournalBook;
import com.renanpontes.isekaimahou.feature.journal.data.JournalChapter;
import com.renanpontes.isekaimahou.feature.journal.data.JournalCondition;
import com.renanpontes.isekaimahou.feature.journal.data.JournalRewards;
import com.renanpontes.isekaimahou.feature.journal.data.JournalStep;
import com.renanpontes.isekaimahou.feature.journal.entity.GuideSpiritEntity;
import com.renanpontes.isekaimahou.feature.journal.story.StoryService;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lógica do Diário no servidor.
 * <ul>
 *   <li>Avalia o capítulo em andamento quando o progresso muda (eventos {@code MagicProgressEvent}, no tick seguinte)
 *       e a cada 100 ticks (para marcadores escritos direto no MagicData, visitas a dimensões, inventário etc.).</li>
 *   <li>Passo cumprido → marcador {@code journal.<capitulo>.<passo>}, aviso "Missão atualizada", XP e recompensas do passo.</li>
 *   <li>Todos os obrigatórios → {@code journal.<capitulo>}, recompensas, desfecho; o próximo começa ~5 s depois
 *       com a abertura e o Espírito Guia.</li>
 *   <li>Objetivo atual com {@code locate}: o {@link JournalLocator} procura a estrutura e manda a seta ao cliente.</li>
 *   <li>Entrada no jogo (v0.9): o capítulo 1 só abre depois do "Parabéns!" da classe ({@code ClassCongratsDone}), com o
 *       título e, logo depois, o Espírito Guia rodando o tutorial ({@link JournalTutorial}). Enquanto
 *       {@code Onboarding.quiet}, nada de chat: passos concluídos viram toasts curtos.</li>
 *   <li>O Espírito Guia fala pela caixa de diálogo do núcleo ({@code Dialogue}), não pelo chat.</li>
 *   <li>v1.0: a ROTA DA CLASSE corre em paralelo (capítulos com {@code "path"}): o seu capítulo em andamento abre depois
 *       do tutorial, com toast + fala do espírito (sem título por cima da história), tem alvo próprio no rastreador e entra
 *       nas dicas do espírito. Depois do final (Dragão), o capítulo SECRETO abre quando o epílogo termina.</li>
 *   <li>Onda 5 — "o Dragão é o fim": a história inteira (masmorras, torres, biomas, Cidade Anã, Cidadela, Biblioteca)
 *       fica ANTES do capítulo final; depois dele só existe o capítulo do Abismo Infinito. O final fecha quando o Dragão
 *       cai, mesmo com capítulos anteriores por fazer (ver {@link #evaluateMain}).</li>
 * </ul>
 */
public final class JournalService {
    private static final int CHECK_INTERVAL = 100;
    /** Pausa entre o fim de um capítulo e a abertura do próximo. */
    private static final int NEXT_CHAPTER_DELAY = 100;
    /** Depois do "Parabéns!" da classe, o título do capítulo 1 espera um instante (a tela de parabéns some). */
    private static final int INTRO_DELAY = 30;
    /** Título do capítulo 1 → o Espírito Guia aparece com o tutorial (o título fica ~4 s na tela). */
    static final int TUTORIAL_DELAY = 80;
    /** Abertura dos outros capítulos: o espírito fala depois que o título passa. */
    private static final int GREET_DELAY = 50;
    /** Diálogos do Espírito Guia (abertura de capítulo e dicas): um substitui o outro na fila. */
    static final String DIALOGUE_GREET = "journal.chapter";
    static final String DIALOGUE_HINT = "journal.hint";
    /** Abertura de um capítulo da rota da classe (fila própria: espera a fala da história terminar). */
    static final String DIALOGUE_CLASS = "journal.class";
    /** A rota da classe abre um pouco depois do fim do tutorial (o espírito acabou de se despedir). */
    private static final int CLASS_BEGIN_DELAY = 200;
    /** Intervalo entre as tentativas de despedida do espírito (ele espera a fala acabar). */
    private static final int DISMISS_RETRY = 60;
    /** Máximo de toasts "Missão concluída" de uma vez durante a entrada no jogo. */
    private static final int MAX_QUIET_TOASTS = 3;
    /**
     * Depois da abertura de um capítulo, espera um pouco antes de conferir os passos: se o jogador já cumpriu tudo
     * (progrediu fora de ordem), o título "Capítulo concluído" não atropela o da abertura.
     */
    private static final int OPENING_HOLD = 80;
    /**
     * Intervalo mínimo entre duas ABERTURAS de capítulo (título + fala do espírito). Só importa quando há dois para
     * abrir quase juntos: o pós-final (Abismo) e um capítulo que ficou para trás.
     */
    private static final int OPENING_GAP = 400;
    /** Recarga do botão "Chamar Espírito Guia". */
    public static final int GUIDE_COOLDOWN = 20 * 30;
    /** Cor do Espírito Guia quando não há capítulo (tutorial, livro vazio). */
    static final int SPIRIT_RGB = 0x9FE8FF;
    private static final int TEXT = 0xFFE9D7A8;
    /** Acima disto, os passos concluídos de uma vez (mundo antigo, progresso fora de ordem) viram um resumo no chat. */
    private static final int MAX_STEP_LINES = 3;

    /** Intervalo entre as linhas do epílogo (efeito de "narração"). */
    private static final int EPILOGUE_INTERVAL = 70;
    /** O final do Diário espera a cena de morte do último chefe (cutscene de 180 ticks do Dragão Primordial). */
    private static final int FINALE_DELAY = 200;
    /**
     * Marcador do final ainda não mostrado (o Dragão caiu, mas a cutscene/epílogo esperam): se o jogador sair do mundo
     * nesse meio-tempo, o final toca quando ele voltar.
     */
    static final String FINALE_PENDING = "journal.finale_pending";

    /** Rosa dos ventos (8 direções, a partir do norte girando para o leste). */
    private static final String[] DIRECTIONS = {
            "screen.isekai_mahou.journal.dir.n", "screen.isekai_mahou.journal.dir.ne", "screen.isekai_mahou.journal.dir.e",
            "screen.isekai_mahou.journal.dir.se", "screen.isekai_mahou.journal.dir.s", "screen.isekai_mahou.journal.dir.sw",
            "screen.isekai_mahou.journal.dir.w", "screen.isekai_mahou.journal.dir.nw"};

    /** Passos de tutorial que o cliente pode avisar (marcador {@code journal.tutorial.<chave>}). */
    private static final Set<String> TUTORIAL_KEYS = Set.of("menu", "grimoire", "spell_built");

    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> NEXT_BEGIN = new HashMap<>();
    /** Próximo capítulo da rota da classe: não abre antes deste momento. */
    private static final Map<UUID, Long> CLASS_NEXT = new HashMap<>();
    /** Jogador → momento até o qual a avaliação fica em espera (abertura de capítulo em andamento). */
    private static final Map<UUID, Long> HOLD = new HashMap<>();
    private static final Map<UUID, Long> GUIDE_CALLED = new HashMap<>();
    /** Mensagens agendadas (epílogo): jogador → fila de (momento, texto). */
    private static final Map<UUID, Deque<Delayed>> QUEUE = new HashMap<>();
    /** Jogador → momento em que o capítulo 1 pode abrir (entrada no jogo, depois do "Parabéns!"). */
    private static final Map<UUID, Long> INTRO_AT = new HashMap<>();
    /** Ações agendadas por jogador (tutorial, fala de abertura, espírito indo embora). Somem quando ele sai. */
    private static final Map<UUID, List<Task>> TASKS = new HashMap<>();
    /** Jogadores com o final já agendado nesta sessão (evita agendar de novo a cada checagem). */
    private static final Set<UUID> FINALE_SCHEDULED = new HashSet<>();

    private record Delayed(long at, Component text, SoundEvent sound) {}

    private record Task(long at, Consumer<ServerPlayer> action) {}

    /** Pede uma avaliação no próximo tick do jogador. */
    public static void markDirty(ServerPlayer player) {
        // Jogadores falsos (máquinas de outros mods) não "tickam": ficariam presos na fila para sempre.
        if (player instanceof FakePlayer) return;
        PENDING.add(player.getUUID());
    }

    public static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean due = PENDING.remove(id) || player.tickCount % CHECK_INTERVAL == 17;
        Long hold = HOLD.get(id);
        if (hold != null) {
            if (now(player) < hold) {
                due = false;
            } else {
                HOLD.remove(id);
                due = true;
            }
        }
        if (due) evaluate(player);
        Deque<Delayed> q = QUEUE.get(player.getUUID());
        if (q != null) {
            long now = now(player);
            while (!q.isEmpty() && q.peekFirst().at() <= now) {
                Delayed m = q.pollFirst();
                player.sendSystemMessage(m.text());
                if (m.sound() != null) player.playNotifySound(m.sound(), SoundSource.PLAYERS, 0.7F, 1.0F);
            }
            if (q.isEmpty()) QUEUE.remove(player.getUUID());
        }
        runTasks(player);
        // Campanha (v1.0): falas e cutscenes de marco esperando a vez.
        StoryService.tick(player);
        // Durante o tutorial o espírito fica ao lado (a duração normal dele é de 2 min).
        if (player.tickCount % 100 == 43 && JournalTutorial.running(player)) keepGuide(player);
    }

    /** Agenda uma ação para daqui a {@code ticks} (some se o jogador sair do mundo). */
    public static void later(ServerPlayer player, int ticks, Consumer<ServerPlayer> action) {
        if (player instanceof FakePlayer) return;
        TASKS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(new Task(now(player) + Math.max(1, ticks), action));
    }

    private static void runTasks(ServerPlayer player) {
        List<Task> list = TASKS.get(player.getUUID());
        if (list == null) return;
        long now = now(player);
        List<Task> due = null;
        for (Task t : list) {
            if (t.at() > now) continue;
            if (due == null) due = new ArrayList<>(2);
            due.add(t);
        }
        if (due == null) return;
        list.removeAll(due);
        if (list.isEmpty()) TASKS.remove(player.getUUID());
        for (Task t : due) t.action().accept(player);
    }

    public static void forget(ServerPlayer player) {
        PENDING.remove(player.getUUID());
        NEXT_BEGIN.remove(player.getUUID());
        CLASS_NEXT.remove(player.getUUID());
        HOLD.remove(player.getUUID());
        GUIDE_CALLED.remove(player.getUUID());
        QUEUE.remove(player.getUUID());
        INTRO_AT.remove(player.getUUID());
        TASKS.remove(player.getUUID());
        FINALE_SCHEDULED.remove(player.getUUID());
        JournalTutorial.forget(player.getUUID());
        JournalLocator.forgetAll(player.getUUID());
    }

    public static void clearAll() {
        PENDING.clear();
        NEXT_BEGIN.clear();
        CLASS_NEXT.clear();
        HOLD.clear();
        GUIDE_CALLED.clear();
        QUEUE.clear();
        INTRO_AT.clear();
        TASKS.clear();
        FINALE_SCHEDULED.clear();
        StoryService.clearAll();
        JournalTutorial.clearAll();
        JournalLocator.clearAll();
    }

    /** Relógio global (o do Mundo Normal; não reinicia ao morrer ou trocar de dimensão). */
    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    // =====================================================================================
    //  Avaliação
    // =====================================================================================
    public static void evaluate(ServerPlayer player) {
        JournalBook book = JournalBook.server();
        // Morto (tela de morte) ou espectador: espera — a abertura e o Espírito Guia ficariam perdidos.
        if (book.isEmpty() || player.isSpectator() || !player.isAlive()) return;
        MagicData d = MagicApi.get(player);
        markVisits(player, d, book);
        resumeFinale(player, d, book.chapters());
        boolean opened = evaluateMain(player, d, book);
        // Um capítulo da história acabou de abrir (título na tela): a rota da classe espera a próxima checagem.
        if (!opened) evaluateClass(player, d, book);
    }

    /**
     * História principal. @return true se um capítulo acabou de abrir agora
     * <p>
     * O "capítulo atual" é o primeiro não concluído (dono do rastreador). Onda 5 — "o Dragão é o fim": além dele,
     * <ul>
     *   <li>o capítulo FINAL fecha assim que o Dragão cai, mesmo bloqueado por capítulos anteriores por fazer (mundo
     *       antigo que ganhou capítulos antes do final, jogador carregado pelo grupo): ninguém perde o final;</li>
     *   <li>o capítulo DEPOIS do final (o Abismo Infinito) anda em paralelo com o que tiver ficado para trás — esses
     *       continuam abrindo um por vez, em ordem.</li>
     * </ul>
     */
    private static boolean evaluateMain(ServerPlayer player, MagicData d, JournalBook book) {
        List<JournalChapter> chapters = book.chapters();
        JournalChapter current = book.active(d);
        for (int i = 0; i < chapters.size(); i++) {
            JournalChapter ch = chapters.get(i);
            JournalBook.State state = book.state(i, d);
            boolean isCurrent = ch == current;
            if (state == JournalBook.State.LOCKED) {
                if (ch.finale() && finaleOutOfOrder(player, d, ch)) return false;
                continue;
            }
            if (state == JournalBook.State.ACTIVE && !d.hasFlag(ch.begunFlag())) {
                // Ainda não é hora de abrir (pausa entre capítulos, final tocando): os outros seguem sendo conferidos.
                if (!canBegin(player, d, i)) continue;
                begin(player, ch, i);
                HOLD.put(player.getUUID(), now(player) + OPENING_HOLD);
                // Se houver outro capítulo para abrir (o pós-final ao lado de um atrasado), ele espera esta abertura passar.
                NEXT_BEGIN.put(player.getUUID(), now(player) + OPENING_GAP);
                if (isCurrent) JournalLocator.clear(player);
                return true;
            }
            List<JournalStep> newly = newlyDone(player, d, ch);
            if (!newly.isEmpty()) completeSteps(player, ch, newly);
            if (state == JournalBook.State.ACTIVE) {
                boolean allDone = ch.required().stream().allMatch(s -> d.hasFlag(s.flag(ch)));
                if (!allDone) {
                    if (isCurrent) JournalLocator.update(player, ch, JournalBook.currentStep(ch, d, player));
                    continue;
                }
                completeChapter(player, ch);
                // Depois do final, o próximo (o capítulo secreto) espera a cutscene e o epílogo inteiros.
                long wait = ch.finale() ? FINALE_DELAY + finaleLength(ch) + NEXT_CHAPTER_DELAY : NEXT_CHAPTER_DELAY;
                NEXT_BEGIN.put(player.getUUID(), now(player) + wait);
                if (isCurrent) JournalLocator.clear(player);
                return false;
            }
        }
        if (current == null) JournalLocator.clear(player);
        return false;
    }

    /**
     * O capítulo final ainda está bloqueado (há capítulos anteriores por fazer), mas o que ele pede já aconteceu — o
     * Dragão caiu. O final fecha agora: recompensas, aviso ao servidor, cutscene e epílogo. Os capítulos que ficaram para
     * trás continuam no Diário, abrindo um a um. Enquanto o Dragão não cai, nada do capítulo é conferido (os passos
     * opcionais dele — o Décimo Círculo — esperam a vez).
     * @return true se o final fechou nesta checagem
     */
    private static boolean finaleOutOfOrder(ServerPlayer player, MagicData d, JournalChapter ch) {
        List<JournalStep> required = ch.required();
        if (required.isEmpty() || !required.stream().allMatch(s -> JournalBook.stepDone(ch, s, d, player))) return false;
        d.setFlag(ch.begunFlag(), true);
        List<JournalStep> newly = newlyDone(player, d, ch);
        if (!newly.isEmpty()) completeSteps(player, ch, newly);
        completeChapter(player, ch);
        NEXT_BEGIN.put(player.getUUID(), now(player) + FINALE_DELAY + finaleLength(ch) + NEXT_CHAPTER_DELAY);
        return true;
    }

    /** Passos de um capítulo que acabaram de ser cumpridos (já marcados). */
    private static List<JournalStep> newlyDone(ServerPlayer player, MagicData d, JournalChapter ch) {
        List<JournalStep> newly = new ArrayList<>();
        for (JournalStep s : ch.visibleSteps()) {
            if (!d.hasFlag(s.flag(ch)) && s.condition().done(d, player)) {
                d.setFlag(s.flag(ch), true);
                newly.add(s);
            }
        }
        return newly;
    }

    /**
     * Rota da classe do jogador: igual à história (passos, recompensas, capítulo concluído), mas paralela — o capítulo
     * em andamento abre só depois do tutorial, sem título na tela (toast + fala do espírito).
     */
    private static void evaluateClass(ServerPlayer player, MagicData d, JournalBook book) {
        List<JournalChapter> track = book.classTrack(d.path());
        for (int i = 0; i < track.size(); i++) {
            JournalChapter ch = track.get(i);
            JournalBook.State state = JournalBook.state(track, i, d);
            if (state == JournalBook.State.LOCKED) break;
            if (state == JournalBook.State.ACTIVE && !d.hasFlag(ch.begunFlag())) {
                if (canBeginClass(player, d, book)) beginClass(player, ch);
                JournalLocator.clear(player, JournalTargets.CLASS);
                return;
            }
            List<JournalStep> newly = newlyDone(player, d, ch);
            if (!newly.isEmpty()) completeSteps(player, ch, newly);
            if (state == JournalBook.State.ACTIVE) {
                boolean allDone = ch.required().stream().allMatch(s -> d.hasFlag(s.flag(ch)));
                if (!allDone) {
                    JournalLocator.update(player, JournalTargets.CLASS, ch, JournalBook.currentStep(ch, d, player));
                    return;
                }
                completeChapter(player, ch);
                CLASS_NEXT.put(player.getUUID(), now(player) + NEXT_CHAPTER_DELAY);
                JournalLocator.clear(player, JournalTargets.CLASS);
                return;
            }
        }
        JournalLocator.clear(player, JournalTargets.CLASS);
    }

    /**
     * A rota da classe pode abrir? Depois do 1º capítulo da história ter aberto e do tutorial (a entrada no jogo fica
     * limpa), sem o final do Dragão tocando, e na hora marcada.
     */
    private static boolean canBeginClass(ServerPlayer player, MagicData d, JournalBook book) {
        if (d.path() == MagicPath.NONE || !Onboarding.tutorialDone(player) || d.hasFlag(FINALE_PENDING)) return false;
        if (!book.isEmpty() && !d.hasFlag(book.chapters().get(0).begunFlag())) return false;
        if (JournalTutorial.running(player)) return false;
        Long at = CLASS_NEXT.get(player.getUUID());
        if (at == null) {
            // 1ª vez nesta sessão: espera um pouco (o espírito acabou de se despedir do tutorial / o jogador acabou de entrar).
            CLASS_NEXT.put(player.getUUID(), now(player) + CLASS_BEGIN_DELAY);
            later(player, CLASS_BEGIN_DELAY + 1, JournalService::markDirty);
            return false;
        }
        return now(player) >= at;
    }

    /** Duração do final (cutscene + epílogo narrado) a partir do momento em que ele começa. */
    private static long finaleLength(JournalChapter ch) {
        return 60L + (long) (ch.epilogue() + 2) * EPILOGUE_INTERVAL;
    }

    /** O final ficou pendente (saiu do mundo antes da cutscene/epílogo): toca agora, uma vez. */
    private static void resumeFinale(ServerPlayer player, MagicData d, List<JournalChapter> chapters) {
        if (!d.hasFlag(FINALE_PENDING) || FINALE_SCHEDULED.contains(player.getUUID())) return;
        for (JournalChapter ch : chapters) {
            if (ch.finale() && d.hasFlag(ch.doneFlag())) {
                FINALE_SCHEDULED.add(player.getUUID());
                later(player, 60, p -> finale(p, ch));
                // O capítulo seguinte (secreto) espera o epílogo terminar.
                NEXT_BEGIN.put(player.getUUID(), now(player) + 60 + finaleLength(ch) + NEXT_CHAPTER_DELAY);
                return;
            }
        }
        d.setFlag(FINALE_PENDING, false);
    }

    /**
     * O capítulo pode abrir agora? O 1º só depois da classe escolhida E do letreiro "Parabéns!" (entrada no jogo v0.9;
     * personagens antigos já contam como "parabéns visto"), e na hora marcada pela abertura ({@link #INTRO_AT}).
     */
    private static boolean canBegin(ServerPlayer player, MagicData d, int index) {
        if (index == 0) {
            if (d.path() == MagicPath.NONE || !Onboarding.congratsDone(player)) return false;
            Long at = INTRO_AT.get(player.getUUID());
            if (at != null && now(player) < at) return false;
            INTRO_AT.remove(player.getUUID());
            return true;
        }
        // O final do Dragão ainda vai tocar: o capítulo seguinte (secreto) espera.
        if (d.hasFlag(FINALE_PENDING)) return false;
        return now(player) >= NEXT_BEGIN.getOrDefault(player.getUUID(), 0L);
    }

    // =====================================================================================
    //  Entrada no jogo (núcleo Onboarding)
    // =====================================================================================
    /**
     * O letreiro "Parabéns!" da classe acabou: o capítulo 1 abre em instantes (título) e o Espírito Guia vem com o
     * tutorial. Se o capítulo já tinha começado (personagem refazendo a entrada), só o tutorial roda de novo.
     */
    static void onCongratsDone(ServerPlayer player) {
        if (player instanceof FakePlayer) return;
        JournalBook book = JournalBook.server();
        if (book.isEmpty()) {
            // Sem capítulos (datapack removeu o livro): o tutorial ainda roda, senão a entrada nunca termina.
            JournalTutorial.schedule(player, INTRO_DELAY, false);
            return;
        }
        JournalChapter first = book.chapters().get(0);
        if (MagicApi.get(player).hasFlag(first.begunFlag())) {
            JournalTutorial.schedule(player, INTRO_DELAY, false);
            return;
        }
        INTRO_AT.put(player.getUUID(), now(player) + INTRO_DELAY);
        HOLD.remove(player.getUUID());
        // Avalia quando a hora chegar (a checagem a cada 100 ticks pode demorar mais que isso).
        later(player, INTRO_DELAY, JournalService::markDirty);
    }

    /** Voltou ao mundo no meio do tutorial: retoma (o capítulo 1 abre primeiro, se ainda não abriu). */
    static void onTutorialResume(ServerPlayer player) {
        if (player instanceof FakePlayer) return;
        JournalBook book = JournalBook.server();
        if (!book.isEmpty() && !MagicApi.get(player).hasFlag(book.chapters().get(0).begunFlag())) {
            INTRO_AT.put(player.getUUID(), now(player) + 10);
            later(player, 10, JournalService::markDirty);
            return;
        }
        JournalTutorial.schedule(player, 10, true);
    }

    /**
     * Tutorial concluído (pelo diálogo, ou pulado por comando — {@code /mahou onboarding skip} fecha a caixa SEM o
     * evento de fim): reavalia e, se o diálogo do tutorial não está mais aberto, o espírito se despede.
     */
    static void onTutorialDone(ServerPlayer player) {
        markDirty(player);
        if (!Dialogue.isOpen(player, JournalTutorial.ID)) later(player, 60, JournalService::dismissGuide);
    }

    /** Marca a visita às dimensões citadas no livro (independe de outros módulos). */
    private static void markVisits(ServerPlayer player, MagicData d, JournalBook book) {
        ResourceLocation here = player.level().dimension().location();
        if (book.dimensions().contains(here)) d.setFlag(JournalCondition.visitFlag(here), true);
    }

    /** O cliente viu um passo do tutorial (abrir o menu, abrir o Grimório, montar uma magia). */
    public static void tutorial(ServerPlayer player, String key) {
        if (!TUTORIAL_KEYS.contains(key)) return;
        MagicData d = MagicApi.get(player);
        String flag = "journal.tutorial." + key;
        if (d.hasFlag(flag)) return;
        // "Montou uma magia" só vale se existe mesmo alguma magia salva (o cliente não decide sozinho).
        if (key.equals("spell_built") && !hasAnySpell(d)) return;
        d.setFlag(flag, true);
        if (!key.equals("menu")) d.setFlag("journal.tutorial.menu", true);
        markDirty(player);
    }

    private static boolean hasAnySpell(MagicData d) {
        if (!d.library().isEmpty()) return true;
        // v1.0: 9 loadouts fixos (sem Roda Elemental), cada um com Z/X/C/G.
        for (int l = 0; l < MagicData.LOADOUTS; l++) {
            for (int s = 0; s < Progression.SLOTS; s++) {
                SpellSlot slot = d.peekSlot(l, s);
                if (!slot.isEmpty()) return true;
            }
        }
        return false;
    }

    // =====================================================================================
    //  Abertura, passos e desfecho
    // =====================================================================================
    private static void begin(ServerPlayer player, JournalChapter ch, int index) {
        MagicData d = MagicApi.get(player);
        d.setFlag(ch.begunFlag(), true);
        // Campanha: a batida de abertura (se houver) entra na fila e fala depois do título e do Espírito Guia.
        StoryService.onChapterBegin(player, ch.id());
        MutableComponent number = Component.translatable("screen.isekai_mahou.journal.chapter_n", index + 1);
        title(player, number.copy().withColor(ch.color()), ch.title().withStyle(ChatFormatting.ITALIC), 15, 70, 25);
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.2F);

        // Entrada no jogo: só o título; o Espírito Guia vem logo depois com a história e o tutorial (sem chat).
        if (index == 0 && !Onboarding.tutorialDone(player)) {
            JournalTutorial.schedule(player, TUTORIAL_DELAY, false);
            return;
        }
        if (!Onboarding.quiet(player)) {
            player.sendSystemMessage(Component.empty());
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.journal.chapter_begin", number, ch.title())
                    .withStyle(s -> s.withColor(ch.color()).withBold(true)));
            player.sendSystemMessage(ch.lore().withStyle(s -> s.withColor(TEXT & 0xFFFFFF).withItalic(true)));
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.journal.open_hint",
                    Component.keybind("key.isekai_mahou.menu").withStyle(ChatFormatting.YELLOW),
                    Component.keybind("key.isekai_mahou.tracker").withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GRAY));
        }
        // Segundo capítulo: o jogador já saiu do básico — uma dica discreta (uma vez) mostra onde fica o "Onde Conseguir".
        if (index == 1) Hints.show(player, "journal_where");
        // O espírito aparece com a fala curta do capítulo e o primeiro objetivo (caixa de diálogo, depois do título).
        later(player, GREET_DELAY, p -> greet(p, ch));
    }

    /**
     * Abertura de um capítulo da ROTA DA CLASSE: sem título na tela (a história pode estar no meio de outro), um toast,
     * a crônica no chat e, logo depois, o espírito conta o capítulo e o primeiro objetivo (fila própria de diálogo).
     */
    private static void beginClass(ServerPlayer player, JournalChapter ch) {
        MagicData d = MagicApi.get(player);
        d.setFlag(ch.begunFlag(), true);
        MagicPath path = ch.magicPath();
        Rewards.toast(player, Component.translatable("toast.isekai_mahou.journal.class_begin", path.displayName()), ch.title(),
                chapterIcon(ch), ch.color());
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.9F);
        if (!Onboarding.quiet(player)) {
            player.sendSystemMessage(Component.empty());
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.journal.class_begin", path.displayName(), ch.title())
                    .withStyle(s -> s.withColor(ch.color()).withBold(true)));
            player.sendSystemMessage(ch.lore().withStyle(s -> s.withColor(TEXT & 0xFFFFFF).withItalic(true)));
        }
        later(player, GREET_DELAY, p -> greetClass(p, ch));
    }

    /** O espírito apresenta a rota da classe (depois de qualquer fala da história que esteja na tela). */
    private static void greetClass(ServerPlayer player, JournalChapter ch) {
        if (!player.isAlive() || JournalTutorial.running(player)) return;
        MagicData d = MagicApi.get(player);
        if (d.hasFlag(ch.doneFlag()) || ch.magicPath() != d.path()) return;
        summonGuide(player, ch, false);
        List<Dialogue.Page> pages = new ArrayList<>(3);
        pages.add(Dialogue.Page.of(ch.intro() ? ch.introLine()
                : Component.translatable("message.isekai_mahou.journal.spirit_class_greet", ch.magicPath().displayName())));
        JournalStep step = JournalBook.currentStep(ch, d, player);
        if (step != null) pages.addAll(objectivePages(player, ch, step));
        spiritSays(player, DIALOGUE_CLASS, ch.color(), pages);
    }

    /** Fala de abertura do capítulo: o espírito surge, conta o capítulo em uma fala e diz o objetivo atual. */
    private static void greet(ServerPlayer player, JournalChapter ch) {
        if (!player.isAlive() || JournalTutorial.running(player)) return;
        MagicData d = MagicApi.get(player);
        if (d.hasFlag(ch.doneFlag())) return;
        // Tudo já cumprido fora de ordem (ex.: o Dragão caiu antes do capítulo dele abrir): o capítulo fecha na próxima
        // checagem — a fala de "vá enfrentá-lo" ficaria sem sentido.
        if (ch.required().stream().allMatch(s -> JournalBook.stepDone(ch, s, d, player))) return;
        summonGuide(player, ch, false);
        List<Dialogue.Page> pages = new ArrayList<>(3);
        pages.add(Dialogue.Page.of(ch.intro() ? ch.introLine()
                : Component.translatable("message.isekai_mahou.journal.spirit_greet")));
        JournalStep step = JournalBook.currentStep(ch, d, player);
        if (step != null) pages.addAll(objectivePages(player, ch, step));
        spiritSays(player, DIALOGUE_GREET, ch.color(), pages);
    }

    /**
     * Passos concluídos nesta checagem: uma linha por passo (ou um resumo, se forem muitos), XP e recompensas. Durante a
     * entrada no jogo ({@code Onboarding.quiet}) nada vai para o chat: só um toast curto por passo.
     */
    private static void completeSteps(ServerPlayer player, JournalChapter ch, List<JournalStep> steps) {
        boolean quiet = Onboarding.quiet(player);
        int shown = quiet ? 0 : steps.size() > MAX_STEP_LINES ? MAX_STEP_LINES - 1 : steps.size();
        int totalXp = 0;
        int toasts = 0;
        MagicData d = MagicApi.get(player);
        for (int k = 0; k < steps.size(); k++) {
            JournalStep step = steps.get(k);
            StoryService.onStep(player, step.uid(ch));
            // Tributo de Ascensão de quem já está naquele Círculo (e afins): o passo vale, o prêmio não.
            boolean skipRewards = step.rewardUnless() != null && step.rewardUnless().done(d, player);
            List<Component> rewards = skipRewards ? List.of() : grantRewards(player, step.rewards());
            if (step.xp() > 0) MagicApi.giveXp(player, step.xp(), "journal");
            totalXp += step.xp() + step.rewards().xp();
            if (k < shown) {
                MutableComponent line = Component.translatable("message.isekai_mahou.journal.step_done", step.title(ch).withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GREEN);
                if (step.optional()) line.append(Component.literal(" ").append(Component.translatable("screen.isekai_mahou.journal.optional")
                        .withStyle(ChatFormatting.DARK_GRAY)));
                if (step.xp() > 0) line.append(Component.literal(" ").append(Component.translatable("screen.isekai_mahou.journal.bonus_xp", step.xp())
                        .withStyle(ChatFormatting.LIGHT_PURPLE)));
                player.sendSystemMessage(line);
                if (!rewards.isEmpty()) player.sendSystemMessage(rewardList("message.isekai_mahou.journal.step_rewards", rewards));
            }
            if (quiet) {
                if (toasts++ < MAX_QUIET_TOASTS) {
                    Rewards.toast(player, Component.translatable("toast.isekai_mahou.journal.step_done"), step.title(ch),
                            stepIcon(ch, step), ch.color());
                }
            } else if (!skipRewards && step.rewards().hasItems()) {
                Rewards.toast(player, Component.translatable("toast.isekai_mahou.journal.step_reward"), step.title(ch),
                        stepIcon(ch, step), ch.color());
            }
        }
        if (quiet) {
            player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.3F);
            return;
        }
        if (steps.size() > shown) {
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.journal.steps_more", steps.size() - shown, totalXp)
                    .withStyle(ChatFormatting.GREEN));
        }
        JournalStep last = steps.get(steps.size() - 1);
        announce(player, Component.translatable("announce.isekai_mahou.journal.updated", last.title(ch)), ch.color());
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    private static void completeChapter(ServerPlayer player, JournalChapter ch) {
        MagicData d = MagicApi.get(player);
        d.setFlag(ch.doneFlag(), true);
        StoryService.onChapterDone(player, ch.id());
        List<Component> rewards = grantRewards(player, ch.rewards());

        if (!Onboarding.quiet(player)) {
            player.sendSystemMessage(Component.empty());
            player.sendSystemMessage(Component.translatable("message.isekai_mahou.journal.chapter_done", ch.title())
                    .withStyle(s -> s.withColor(ch.color()).withBold(true)));
            player.sendSystemMessage(ch.outro().withStyle(s -> s.withColor(TEXT & 0xFFFFFF).withItalic(true)));
            if (!rewards.isEmpty()) player.sendSystemMessage(rewardList("message.isekai_mahou.journal.rewards", rewards));
        }
        Rewards.toast(player, Component.translatable("toast.isekai_mahou.journal.chapter_done"), ch.title(), chapterIcon(ch), ch.color());

        ServerLevel level = player.serverLevel();
        Vec3 pos = player.position().add(0, 1, 0);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.x, pos.y, pos.z, 40, 0.6, 0.8, 0.6, 0.35);
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);

        if (ch.finale()) {
            // O aviso para o servidor sai agora; a cutscene e o epílogo esperam a cena de morte do último chefe (o Dragão
            // toca a sua própria cutscene de ~9 s ao cair — a do Diário por cima a cortaria).
            Component broadcast = Component.translatable("message.isekai_mahou.journal.legend_broadcast", player.getDisplayName())
                    .withStyle(s -> s.withColor(ch.color()).withBold(true));
            player.server.getPlayerList().broadcastSystemMessage(broadcast, false);
            d.setFlag(FINALE_PENDING, true);
            FINALE_SCHEDULED.add(player.getUUID());
            later(player, FINALE_DELAY, p -> finale(p, ch));
        } else if (ch.isClassRoute()) {
            title(player, Component.translatable("title.isekai_mahou.journal.class_done").withColor(ch.color()),
                    ch.title().withStyle(ChatFormatting.ITALIC), 10, 60, 20);
            announce(player, Component.translatable("announce.isekai_mahou.journal.class_done", ch.magicPath().displayName(), ch.title()),
                    ch.color());
        } else {
            title(player, Component.translatable("title.isekai_mahou.journal.chapter_done").withColor(ch.color()),
                    ch.title().withStyle(ChatFormatting.ITALIC), 10, 60, 20);
            announce(player, Component.translatable("announce.isekai_mahou.journal.chapter_done", ch.title()), ch.color());
        }
    }

    /** Desfecho do último capítulo: cutscene, título épico, chuva de luz e aviso para o servidor inteiro. */
    private static void finale(ServerPlayer player, JournalChapter ch) {
        if (!player.isAlive() || player.isSpectator()) {
            // Na tela de morte a cutscene se perderia: espera ele voltar.
            later(player, 40, p -> finale(p, ch));
            return;
        }
        MagicApi.get(player).setFlag(FINALE_PENDING, false);
        Component title = Component.translatable("title.isekai_mahou.journal.finale");
        Component subtitle = Component.translatable("title.isekai_mahou.journal.finale_sub");
        // A cutscene (módulo cinematic) já mostra título/subtítulo em tela cheia, ou um anúncio se o jogador desligou
        // as cutscenes: sem o título do vanilla por cima.
        Cutscenes.play(player, "ascension", player, title, subtitle, ch.color(), 160, false);
        ServerLevel level = player.serverLevel();
        ElementFx.burst(level, player.position().add(0, 1, 0), Element.TWILIGHT, 80, 2.0);
        ElementFx.burst(level, player.position().add(0, 1.5, 0), Element.LIGHT, 40, 1.5);
        player.playNotifySound(SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.0F);
        player.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 0.7F);
        // Epílogo narrado: uma linha a cada ~3,5 s, depois da cutscene.
        Deque<Delayed> q = QUEUE.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
        long at = now(player) + 60;
        q.add(new Delayed(at, Component.empty(), null));
        for (int i = 1; i <= ch.epilogue(); i++) {
            at += EPILOGUE_INTERVAL;
            q.add(new Delayed(at, ch.epilogueLine(i).withStyle(s -> s.withColor(ch.color()).withItalic(true)), SoundEvents.AMETHYST_BLOCK_CHIME));
        }
        q.add(new Delayed(at + EPILOGUE_INTERVAL, Component.translatable("message.isekai_mahou.journal.the_end")
                .withStyle(s -> s.withColor(ch.color()).withBold(true)), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE));
    }

    // =====================================================================================
    //  Recompensas
    // =====================================================================================
    /** Entrega as recompensas e devolve a lista do que foi entregue (para o chat). */
    private static List<Component> grantRewards(ServerPlayer player, JournalRewards r) {
        List<Component> out = new ArrayList<>();
        if (r.isEmpty()) return out;
        if (r.xp() > 0) {
            MagicApi.giveXp(player, r.xp(), "journal");
            out.add(Component.translatable("screen.isekai_mahou.journal.reward_xp", r.xp()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        for (JournalRewards.ItemReward ir : r.items()) {
            Item item = BuiltInRegistries.ITEM.getOptional(ir.id()).orElse(null);
            if (item == null || item == Items.AIR) continue;
            giveItem(player, new ItemStack(item, ir.count()), out);
        }
        MagicData d = MagicApi.get(player);
        if (r.elementEssences() > 0) {
            boolean any = false;
            for (Element e : Element.BASE) {
                if (!d.isAwakened(e) || CoreRegistries.ESSENCES.get(e) == null) continue;
                giveItem(player, new ItemStack(CoreRegistries.ESSENCES.get(e).get(), r.elementEssences()), out);
                any = true;
            }
            if (!any) giveItem(player, new ItemStack(CoreRegistries.ESSENCES.get(Element.ARCANE).get(), r.elementEssences()), out);
        }
        List<String> legendsGiven = new ArrayList<>();
        for (int i = 0; i < r.legendScrolls(); i++) {
            ItemStack legend = legendScroll(player, d, legendsGiven);
            if (legend.isEmpty()) {
                giveItem(player, new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 5), out);
                continue;
            }
            out.add(Component.translatable("message.isekai_mahou.journal.reward_legend", legend.getHoverName()).withStyle(ChatFormatting.GOLD));
            Rewards.give(player, legend);
        }
        List<ResourceLocation> given = new ArrayList<>();
        for (ResourceLocation id : r.glyphs()) {
            if (GlyphRegistry.get(id) == null) continue;
            // Glifo já conhecido: o pergaminho seria inútil — vira cristais de mana.
            if (d.knowsGlyph(id)) {
                giveItem(player, new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 2), out);
                continue;
            }
            given.add(id);
        }
        for (int i = 0; i < r.scrolls(); i++) {
            Glyph g = randomGlyph(d, r.scrollTier(), given, player);
            if (g != null) {
                given.add(g.id());
            } else {
                // Já sabe tudo o que poderia aprender neste tier: troca por cristais de mana.
                giveItem(player, new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 3), out);
            }
        }
        for (ResourceLocation id : given) {
            Glyph g = GlyphRegistry.get(id);
            Rewards.give(player, GlyphScrollItem.of(id));
            out.add(Component.translatable("message.isekai_mahou.journal.reward_glyph", g.displayName()).withStyle(ChatFormatting.YELLOW));
        }
        return out;
    }

    /**
     * Pergaminho Lendário que o jogador consiga USAR: primeiro uma lenda que ele já conjura (o parser aceita a receita
     * com os glifos, elementos e Círculo dele), depois uma do Arcano ou de um elemento despertado dentro do Círculo
     * (falta só aprender glifos), e só então qualquer uma. Evita repetir lendas que já estão na Biblioteca.
     */
    private static ItemStack legendScroll(ServerPlayer player, MagicData d, List<String> exclude) {
        List<Legends.Legend> castable = new ArrayList<>();
        List<Legends.Legend> reachable = new ArrayList<>();
        for (Legends.Legend l : Legends.all()) {
            if (exclude.contains(l.id()) || l.minCircle() > d.circle() || inLibrary(d, l)) continue;
            if (l.element() != Element.ARCANE && !d.isAwakened(l.element())) continue;
            reachable.add(l);
            if (castableBy(l, d)) castable.add(l);
        }
        List<Legends.Legend> pool = !castable.isEmpty() ? castable : reachable;
        if (pool.isEmpty()) return Legends.randomScroll(player.getRandom(), d.circle());
        Legends.Legend pick = pool.get(player.getRandom().nextInt(pool.size()));
        exclude.add(pick.id());
        return Legends.scroll(pick);
    }

    /** O parser aceita a lenda com os glifos/elementos/Círculo do jogador? (uma receita quebrada de outro módulo = não). */
    private static boolean castableBy(Legends.Legend legend, MagicData d) {
        try {
            return SpellParser.parse(legend.recipe(), legend.element(), d).ok();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean inLibrary(MagicData d, Legends.Legend legend) {
        for (SpellRecipe r : d.library()) if (r.glyphs().equals(legend.recipe().glyphs())) return true;
        return false;
    }

    private static void giveItem(ServerPlayer player, ItemStack stack, List<Component> out) {
        out.add(Component.translatable("screen.isekai_mahou.journal.reward_item", stack.getCount(), stack.getHoverName()).withStyle(ChatFormatting.AQUA));
        Rewards.give(player, stack);
    }

    private static MutableComponent rewardList(String key, List<Component> rewards) {
        MutableComponent list = Component.empty();
        for (int i = 0; i < rewards.size(); i++) {
            if (i > 0) list.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            list.append(rewards.get(i));
        }
        return Component.translatable(key, list).withStyle(ChatFormatting.GOLD);
    }

    /**
     * Glifo que o jogador ainda não sabe e consegue usar JÁ (Arcano, elemento despertado ou da sua origem, e Círculo suficiente),
     * do tier mais alto disponível.
     */
    private static Glyph randomGlyph(MagicData d, int maxTier, List<ResourceLocation> exclude, ServerPlayer player) {
        List<Glyph> pool = new ArrayList<>();
        int best = 0;
        for (Glyph g : GlyphRegistry.all()) {
            if (g.isLegendary() || g.tier() > maxTier || g.minCircle() > d.circle() || d.knowsGlyph(g.id()) || exclude.contains(g.id())) continue;
            boolean usable = g.origin() != null ? g.origin() == d.path()
                    : g.element() == Element.ARCANE || d.isAwakened(g.element());
            if (!usable) continue;
            if (g.tier() > best) {
                best = g.tier();
                pool.clear();
            }
            if (g.tier() == best) pool.add(g);
        }
        return pool.isEmpty() ? null : pool.get(player.getRandom().nextInt(pool.size()));
    }

    static ItemStack chapterIcon(JournalChapter ch) {
        for (ResourceLocation id : ch.icons()) {
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
            if (item != null && item != Items.AIR) return new ItemStack(item);
        }
        return new ItemStack(Items.WRITABLE_BOOK);
    }

    private static ItemStack stepIcon(JournalChapter ch, JournalStep step) {
        if (step.icon() != null) {
            Item item = BuiltInRegistries.ITEM.getOptional(step.icon()).orElse(null);
            if (item != null && item != Items.AIR) return new ItemStack(item);
        }
        return chapterIcon(ch);
    }

    // =====================================================================================
    //  Espírito Guia
    // =====================================================================================
    /** Botão da aba: chama o espírito (com recarga) e ele mostra o caminho até o objetivo, se houver um alvo. */
    public static void callGuide(ServerPlayer player) {
        long now = now(player);
        Long last = GUIDE_CALLED.get(player.getUUID());
        if (last != null && now - last < GUIDE_COOLDOWN) {
            long left = (GUIDE_COOLDOWN - (now - last) + 19) / 20;
            player.displayClientMessage(Component.translatable("message.isekai_mahou.journal.guide_cooldown", left).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        GUIDE_CALLED.put(player.getUUID(), now);
        summonAndGuide(player, false);
        player.displayClientMessage(Component.translatable("message.isekai_mahou.journal.guide_called").withStyle(ChatFormatting.AQUA), true);
    }

    /**
     * Faz o espírito surgir e, se o objetivo atual tem um lugar, voar na direção dele deixando um rastro. Sem lugar para
     * mostrar, ele diz o objetivo e a dica (o chamado nunca fica sem resposta). Tudo pela caixa de diálogo.
     */
    static void summonAndGuide(ServerPlayer player, boolean greet) {
        if (JournalTutorial.running(player)) {
            keepGuide(player);
            return;
        }
        JournalBook book = JournalBook.server();
        MagicData d = MagicApi.get(player);
        JournalChapter ch = book.active(d);
        if (ch != null && !d.hasFlag(ch.begunFlag())) ch = null;
        JournalChapter cls = openClassChapter(book, d);
        JournalChapter colorFrom = ch != null ? ch : cls != null ? cls
                : !book.isEmpty() ? book.chapters().get(book.chapters().size() - 1) : null;
        GuideSpiritEntity spirit = summonGuide(player, colorFrom, false);
        int rgb = colorFrom == null ? SPIRIT_RGB : colorFrom.color();
        JournalStep step = ch == null ? null : JournalBook.currentStep(ch, d, player);
        JournalStep clsStep = cls == null ? null : JournalBook.currentStep(cls, d, player);
        if (step == null && clsStep == null) {
            if (!greet) {
                String key = book.allDone(d) ? "message.isekai_mahou.journal.hint_complete" : "message.isekai_mahou.journal.hint_none";
                spiritSays(player, DIALOGUE_HINT, rgb, List.of(Dialogue.Page.of(Component.translatable(key))));
            }
            return;
        }
        List<Dialogue.Page> pages = new ArrayList<>(6);
        // Para onde voar: o lugar do objetivo da história; sem lugar, o da rota da classe.
        BlockPos target = null;
        if (step != null) {
            JournalLocator.update(player, ch, step);
            target = JournalLocator.targetNow(player);
            pages.addAll(objectivePages(player, ch, step));
        }
        if (clsStep != null) {
            JournalLocator.update(player, JournalTargets.CLASS, cls, clsStep);
            if (target == null) target = JournalLocator.targetNow(player, JournalTargets.CLASS);
            pages.addAll(classPages(player, cls, clsStep));
        }
        if (target != null) {
            Vec3 goal = Vec3.atCenterOf(target);
            if (spirit != null) spirit.guideTo(goal);
            // Voando: a direção primeiro (o rastro já mostra o caminho), depois o objetivo.
            pages.add(0, Dialogue.Page.of(directionLine("message.isekai_mahou.journal.spirit_follow", player, goal)));
        } else if (step != null && JournalLocator.targetElsewhere(player, ch, step)) {
            pages.add(Dialogue.Page.of(Component.translatable("message.isekai_mahou.journal.spirit_elsewhere")));
        } else if (step != null && step.locate() != null && JournalLocator.nothingNearby(player)) {
            pages.add(Dialogue.Page.of(Component.translatable("message.isekai_mahou.journal.spirit_lost")));
        }
        spiritSays(player, DIALOGUE_HINT, rgb, pages);
    }

    /** Capítulo da rota da classe em andamento e já aberto (ou null). */
    @Nullable
    private static JournalChapter openClassChapter(JournalBook book, MagicData d) {
        JournalChapter cls = book.classActive(d);
        return cls != null && d.hasFlag(cls.begunFlag()) ? cls : null;
    }

    /** Objetivo da rota da classe numa página: "Rota do Ferreiro: X" + dica curta; e a dica completa. */
    private static List<Dialogue.Page> classPages(ServerPlayer player, JournalChapter cls, JournalStep step) {
        MagicPath path = MagicApi.get(player).path();
        MutableComponent head = Component.translatable("message.isekai_mahou.journal.hint_class_objective",
                cls.magicPath().displayName(), step.title(cls).withStyle(ChatFormatting.WHITE))
                .withStyle(s -> s.withColor(cls.color()).withBold(true));
        head.append(Component.literal("\n")).append(Component.translatable(step.tipKey(cls, path))
                .withStyle(s -> s.withColor(ChatFormatting.GRAY).withBold(false)));
        return List.of(Dialogue.Page.of(head), Dialogue.Page.of(step.hint(cls, path)));
    }

    /** Faz o Espírito Guia surgir ao lado do jogador (ou renova o que já está lá). */
    @Nullable
    public static GuideSpiritEntity summonGuide(ServerPlayer player, @Nullable JournalChapter ch, boolean greet) {
        ServerLevel level = player.serverLevel();
        int color = ch == null ? 0xFFE27A : ch.color();
        GuideSpiritEntity spirit = findGuide(player);
        Vec3 spot = guideSpot(player);
        if (spirit == null) {
            spirit = JournalContent.GUIDE_SPIRIT.get().create(level);
            if (spirit == null) return null;
            spirit.setOwner(player);
            spirit.setColor(color);
            spirit.moveTo(spot.x, spot.y, spot.z, player.getYRot() + 180F, 0F);
            level.addFreshEntity(spirit);
        } else {
            spirit.setColor(color);
            spirit.refresh();
            spirit.moveTo(spot.x, spot.y, spot.z, spirit.getYRot(), 0F);
        }
        level.sendParticles(player, ParticleTypes.END_ROD, true, spot.x, spot.y + 0.6, spot.z, 24, 0.3, 0.4, 0.3, 0.06);
        player.playNotifySound(SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.NEUTRAL, 1.0F, 1.2F);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.0F);
        if (greet) {
            spiritSays(player, DIALOGUE_GREET, color,
                    List.of(Dialogue.Page.of(Component.translatable("message.isekai_mahou.journal.spirit_greet"))));
        }
        return spirit;
    }

    /** O espírito deste jogador que ainda está no mundo (perto dele), ou null. */
    @Nullable
    static GuideSpiritEntity findGuide(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(GuideSpiritEntity.class, player.getBoundingBox().inflate(64),
                e -> e.isOwnedBy(player) && !e.isLeaving()).stream().findFirst().orElse(null);
    }

    /** Renova a duração do espírito (tutorial longo); se ele sumiu (troca de dimensão), aparece de novo. */
    private static void keepGuide(ServerPlayer player) {
        GuideSpiritEntity spirit = findGuide(player);
        if (spirit != null) {
            spirit.refresh();
        } else if (player.isAlive() && !player.isSpectator()) {
            JournalBook book = JournalBook.server();
            summonGuide(player, book.isEmpty() ? null : book.chapters().get(0), false);
        }
    }

    /**
     * O espírito se despede (desfaz-se em luz) quando terminar de falar: com uma fala dele ainda aberta, espera mais um
     * pouco; durante o tutorial ele fica (o fim do tutorial agenda a despedida).
     */
    static void dismissGuide(ServerPlayer player) {
        if (JournalTutorial.running(player)) return;
        GuideSpiritEntity spirit = findGuide(player);
        // Já foi embora (duração acabou, outra dimensão): nada a esperar — sem laço de novas tentativas.
        if (spirit == null) return;
        if (Dialogue.isOpen(player, DIALOGUE_GREET) || Dialogue.isOpen(player, DIALOGUE_HINT) || Dialogue.isOpen(player, DIALOGUE_CLASS)
                || StoryService.talking(player)) {
            later(player, DISMISS_RETRY, JournalService::dismissGuide);
            return;
        }
        spirit.vanish();
    }

    /** Clique no espírito: ele vai embora depois de dar a dica. */
    public static void dismissLater(ServerPlayer player) {
        later(player, DISMISS_RETRY, JournalService::dismissGuide);
    }

    /** Lugar livre perto do rosto do jogador (à frente e à esquerda; senão logo à frente; senão acima). */
    private static Vec3 guideSpot(ServerPlayer player) {
        float yaw = player.getYRot() * ((float) Math.PI / 180F);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 base = player.position().add(0, player.getEyeHeight() - 0.55, 0);
        Vec3[] tries = {base.add(forward.scale(1.6)).add(right.scale(-1.2)), base.add(forward.scale(1.4)), base.add(0, 1.4, 0)};
        for (Vec3 v : tries) {
            BlockPos pos = BlockPos.containing(v.add(0, 0.6, 0));
            if (player.level().getBlockState(pos).getCollisionShape(player.level(), pos).isEmpty()) return v;
        }
        return tries[2];
    }

    /**
     * Clique no espírito: dica do objetivo atual (e a direção, quando há um lugar para ir), pela caixa de diálogo.
     * @return true se o espírito deve ir embora depois de falar (durante o tutorial ele fica)
     */
    public static boolean deliverHint(ServerPlayer player) {
        if (JournalTutorial.running(player)) {
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.7F);
            return false;
        }
        JournalBook book = JournalBook.server();
        MagicData d = MagicApi.get(player);
        JournalChapter ch = book.active(d);
        if (ch != null && !d.hasFlag(ch.begunFlag())) ch = null;
        JournalChapter cls = openClassChapter(book, d);
        if (ch == null && cls == null) {
            String key = book.allDone(d) ? "message.isekai_mahou.journal.hint_complete" : "message.isekai_mahou.journal.hint_none";
            spiritSays(player, DIALOGUE_HINT, SPIRIT_RGB, List.of(Dialogue.Page.of(Component.translatable(key))));
            return true;
        }
        JournalStep step = ch == null ? null : JournalBook.currentStep(ch, d, player);
        JournalStep clsStep = cls == null ? null : JournalBook.currentStep(cls, d, player);
        int rgb = ch != null ? ch.color() : cls.color();
        if (step == null && clsStep == null) {
            spiritSays(player, DIALOGUE_HINT, rgb,
                    List.of(Dialogue.Page.of(Component.translatable("message.isekai_mahou.journal.hint_wait"))));
            return true;
        }
        List<Dialogue.Page> pages = new ArrayList<>(6);
        if (step != null) {
            pages.addAll(objectivePages(player, ch, step));
            JournalLocator.update(player, ch, step);
            BlockPos target = JournalLocator.targetNow(player);
            if (target != null) {
                pages.add(Dialogue.Page.of(directionLine("message.isekai_mahou.journal.spirit_direction", player, Vec3.atCenterOf(target))));
            } else if (JournalLocator.targetElsewhere(player, ch, step)) {
                pages.add(Dialogue.Page.of(Component.translatable("message.isekai_mahou.journal.spirit_elsewhere")));
            } else if (step.locate() != null && JournalLocator.nothingNearby(player)) {
                pages.add(Dialogue.Page.of(Component.translatable("message.isekai_mahou.journal.spirit_lost")));
            }
        }
        if (clsStep != null) {
            pages.addAll(classPages(player, cls, clsStep));
            JournalLocator.update(player, JournalTargets.CLASS, cls, clsStep);
            BlockPos target = JournalLocator.targetNow(player, JournalTargets.CLASS);
            if (target != null) {
                pages.add(Dialogue.Page.of(directionLine("message.isekai_mahou.journal.spirit_direction", player, Vec3.atCenterOf(target))));
            }
        }
        spiritSays(player, DIALOGUE_HINT, rgb, pages);
        return true;
    }

    /** "Objetivo atual: X" (+ dica curta) e a dica completa — na variante da classe do jogador quando o passo tem uma. */
    private static List<Dialogue.Page> objectivePages(ServerPlayer player, JournalChapter ch, JournalStep step) {
        MagicPath path = MagicApi.get(player).path();
        MutableComponent head = Component.translatable("message.isekai_mahou.journal.hint_objective",
                step.title(ch).withStyle(ChatFormatting.WHITE)).withStyle(s -> s.withColor(ch.color()).withBold(true));
        head.append(Component.literal("\n")).append(Component.translatable(step.tipKey(ch, path))
                .withStyle(s -> s.withColor(ChatFormatting.GRAY).withBold(false)));
        return List.of(Dialogue.Page.of(head), Dialogue.Page.of(step.hint(ch, path)));
    }

    /** O Espírito Guia fala (caixa de diálogo do núcleo, com o nome dele na cor do capítulo). */
    static void spiritSays(ServerPlayer player, String dialogueId, int rgb, List<Dialogue.Page> pages) {
        if (pages.isEmpty()) return;
        Dialogue.show(player, dialogueId, Component.translatable("entity.isekai_mahou.guide_spirit"), rgb, pages);
    }

    /** "fica a ~N blocos, ao Nordeste" (rosa dos ventos de 8 direções; norte = -Z). */
    private static MutableComponent directionLine(String key, ServerPlayer player, Vec3 target) {
        double dx = target.x - player.getX(), dz = target.z - player.getZ();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        // Ângulo a partir do norte, girando para o leste: 0 = N, 90 = L, 180 = S, 270 = O.
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = Mth.positiveModulo((int) Math.floor((angle + 22.5) / 45.0), 8);
        Component dir = Component.translatable(DIRECTIONS[sector]).withStyle(ChatFormatting.YELLOW);
        return Component.translatable(key, Component.literal(String.valueOf(dist)).withStyle(ChatFormatting.YELLOW), dir);
    }

    // =====================================================================================
    //  Comandos de teste
    // =====================================================================================
    /** Conclui o capítulo em andamento da história (marca os obrigatórios e dá as recompensas). Retorna o capítulo ou null. */
    @Nullable
    public static JournalChapter forceComplete(ServerPlayer player) {
        return forceComplete(player, false);
    }

    /** Conclui o capítulo em andamento da história ou da rota da classe ({@code classRoute}). */
    @Nullable
    public static JournalChapter forceComplete(ServerPlayer player, boolean classRoute) {
        JournalBook book = JournalBook.server();
        MagicData d = MagicApi.get(player);
        JournalChapter ch = classRoute ? book.classActive(d) : book.active(d);
        if (ch == null) return null;
        if (!d.hasFlag(ch.begunFlag())) {
            if (classRoute) beginClass(player, ch);
            else begin(player, ch, book.indexOf(ch));
        }
        for (JournalStep s : ch.required()) d.setFlag(s.flag(ch), true);
        NEXT_BEGIN.remove(player.getUUID());
        CLASS_NEXT.put(player.getUUID(), 0L);
        HOLD.remove(player.getUUID());
        evaluate(player);
        return ch;
    }

    /**
     * Teste (onda 5): põe um capítulo no estado pedido SEM recompensas, títulos nem falas — para montar na mão a situação
     * de um mundo antigo (ex.: Dragão vencido e os capítulos novos por fazer). {@code done=false} apaga os marcadores do
     * capítulo (concluído, aberto e passos).
     */
    public static void forceMark(ServerPlayer player, JournalChapter ch, boolean done) {
        MagicData d = MagicApi.get(player);
        if (done) {
            d.setFlag(ch.begunFlag(), true);
            for (JournalStep s : ch.required()) d.setFlag(s.flag(ch), true);
            d.setFlag(ch.doneFlag(), true);
        } else {
            String prefix = ch.doneFlag() + ".";
            List<String> flags = d.flags().stream().filter(f -> f.equals(ch.doneFlag()) || f.startsWith(prefix)).toList();
            flags.forEach(f -> d.setFlag(f, false));
        }
        if (ch.finale()) d.setFlag(FINALE_PENDING, false);
        NEXT_BEGIN.remove(player.getUUID());
        HOLD.remove(player.getUUID());
        markDirty(player);
    }

    /** Apaga todo o progresso do Diário do jogador. Retorna quantos marcadores foram removidos. */
    public static int reset(ServerPlayer player) {
        MagicData d = MagicApi.get(player);
        List<String> flags = d.flags().stream().filter(f -> f.startsWith("journal.")).toList();
        flags.forEach(f -> d.setFlag(f, false));
        forget(player);
        Dialogue.close(player, JournalTutorial.ID);
        Dialogue.close(player, DIALOGUE_GREET);
        Dialogue.close(player, DIALOGUE_HINT);
        Dialogue.close(player, DIALOGUE_CLASS);
        JournalLocator.clear(player);
        JournalLocator.clear(player, JournalTargets.CLASS);
        markDirty(player);
        return flags.size();
    }

    // =====================================================================================
    //  Utilidades
    // =====================================================================================
    /**
     * Anúncio "de sistema" só para este jogador (o módulo cinematic desenha a faixa no topo da tela).
     * Sem entidade de origem (id -1): é a voz do Diário, não uma fala — nada de balão sobre a cabeça do jogador.
     */
    private static void announce(ServerPlayer player, Component text, int rgb) {
        PacketDistributor.sendToPlayer(player, new CorePayloads.AnnounceText(-1, text, rgb, Announce.STYLE_SYSTEM));
    }

    private static void title(ServerPlayer player, Component title, Component subtitle, int in, int stay, int out) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private JournalService() {}
}
