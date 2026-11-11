package com.renanpontes.isekaimahou.feature.journal.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * O "livro" com todos os capítulos, em ordem. Existem duas cópias:
 * a do SERVIDOR (lida dos datapacks pelo {@link JournalLoader}) e a do CLIENTE (recebida pelo pacote de sincronização,
 * no mesmo formato JSON). Em mundo local as duas convivem na mesma JVM, por isso ficam separadas.
 * <p>
 * v1.0: DUAS trilhas. {@link #chapters()} é a história principal (rota de chefes comum a todos); {@link #classTrack}
 * devolve a ROTA DA CLASSE do jogador (capítulos com {@code "path"}), que anda em paralelo com a história, com o seu
 * próprio capítulo "em andamento". Capítulos sem nenhum passo obrigatório que exista neste jogo (módulo ausente) são
 * descartados na leitura — a história nunca trava nem mostra páginas vazias.
 */
public final class JournalBook {
    public static final JournalBook EMPTY = new JournalBook(List.of(), Map.of(), "[]");

    private static volatile JournalBook server = EMPTY;
    private static volatile JournalBook client = EMPTY;
    /** Incrementado a cada livro recebido no cliente (abas detectam mudanças). */
    public static volatile int clientVersion;

    private final List<JournalChapter> chapters;
    private final Map<String, List<JournalChapter>> classTracks;
    private final String json;
    private final Set<ResourceLocation> dimensions;
    private final Set<ResourceLocation> placed;

    private JournalBook(List<JournalChapter> chapters, Map<String, List<JournalChapter>> classTracks, String json) {
        this.chapters = chapters;
        this.classTracks = classTracks;
        this.json = json;
        Set<ResourceLocation> dims = new LinkedHashSet<>();
        Set<ResourceLocation> blocks = new HashSet<>();
        for (JournalChapter c : all()) {
            for (JournalStep s : c.steps()) {
                s.condition().collectDimensions(dims);
                s.condition().collectPlaced(blocks);
            }
        }
        this.dimensions = Collections.unmodifiableSet(dims);
        this.placed = Collections.unmodifiableSet(blocks);
    }

    /** História principal (sem os capítulos de classe), em ordem. */
    public List<JournalChapter> chapters() { return chapters; }

    /** Rota de uma classe (vazia se a classe não tem capítulos), em ordem. */
    public List<JournalChapter> classTrack(MagicPath path) {
        return path == null || path == MagicPath.NONE ? List.of() : classTracks.getOrDefault(path.id, List.of());
    }

    /** Todos os capítulos (história + rotas de todas as classes). */
    public List<JournalChapter> all() {
        if (classTracks.isEmpty()) return chapters;
        List<JournalChapter> out = new ArrayList<>(chapters);
        classTracks.values().forEach(out::addAll);
        return out;
    }

    /** JSON (lista de capítulos) enviado ao cliente. */
    public String json() { return json; }
    /** Dimensões citadas em condições "visit". */
    public Set<ResourceLocation> dimensions() { return dimensions; }
    /** Blocos citados em condições "placed" (o servidor conta a colocação deles). */
    public Set<ResourceLocation> placedBlocks() { return placed; }
    public boolean isEmpty() { return chapters.isEmpty(); }

    public static JournalBook server() { return server; }
    public static JournalBook client() { return client; }

    static void setServer(JournalBook book) { server = book; }

    public static void setClient(String json) {
        client = parseArray(json);
        clientVersion++;
    }

    public static void clearClient() {
        client = EMPTY;
        clientVersion++;
    }

    /** Posição na SUA trilha (história ou rota da classe). */
    public int indexOf(JournalChapter chapter) {
        if (chapter.isClassRoute()) return classTracks.getOrDefault(chapter.path(), List.of()).indexOf(chapter);
        return chapters.indexOf(chapter);
    }

    /** Busca nas duas trilhas. */
    @Nullable
    public JournalChapter byId(String id) {
        for (JournalChapter c : chapters) if (c.id().equals(id)) return c;
        for (List<JournalChapter> track : classTracks.values()) {
            for (JournalChapter c : track) if (c.id().equals(id)) return c;
        }
        return null;
    }

    // =====================================================================================
    //  Estado de um jogador (servidor e cliente)
    // =====================================================================================
    public enum State { LOCKED, ACTIVE, DONE }

    /**
     * Concluído (marcador), em andamento (TODOS os anteriores concluídos) ou bloqueado — na história principal. Exigir
     * todos os anteriores (e não só o imediatamente anterior) mantém um único capítulo em andamento quando uma versão
     * nova insere capítulos no meio da história de um mundo antigo.
     */
    public State state(int index, MagicData d) {
        return state(chapters, index, d);
    }

    /** Estado na trilha informada (a história ou a rota de uma classe). */
    public static State state(List<JournalChapter> track, int index, MagicData d) {
        JournalChapter c = track.get(index);
        if (d.hasFlag(c.doneFlag())) return State.DONE;
        for (int i = 0; i < index; i++) {
            if (!d.hasFlag(track.get(i).doneFlag())) return State.LOCKED;
        }
        return State.ACTIVE;
    }

    /** Estado de um capítulo de qualquer trilha. */
    public State stateOf(JournalChapter chapter, MagicData d) {
        List<JournalChapter> track = chapter.isClassRoute() ? classTracks.getOrDefault(chapter.path(), List.of()) : chapters;
        int i = track.indexOf(chapter);
        return i < 0 ? State.LOCKED : state(track, i, d);
    }

    /** Capítulo em andamento da história (o primeiro não concluído), ou null se todos foram concluídos. */
    @Nullable
    public JournalChapter active(MagicData d) {
        return firstOpen(chapters, d);
    }

    /** Capítulo em andamento da rota da classe do jogador, ou null (sem rota ou rota completa). */
    @Nullable
    public JournalChapter classActive(MagicData d) {
        return firstOpen(classTrack(d.path()), d);
    }

    @Nullable
    private static JournalChapter firstOpen(List<JournalChapter> track, MagicData d) {
        for (JournalChapter c : track) {
            if (!d.hasFlag(c.doneFlag())) return c;
        }
        return null;
    }

    public boolean allDone(MagicData d) {
        return !chapters.isEmpty() && active(d) == null;
    }

    /** Capítulos concluídos da história (os secretos só contam depois de aparecerem). */
    public int doneCount(MagicData d) {
        int n = 0;
        for (JournalChapter c : chapters) if (d.hasFlag(c.doneFlag())) n++;
        return n;
    }

    /** Total de capítulos da história que o jogador enxerga (os secretos só depois de começarem). */
    public int visibleCount(MagicData d) {
        int n = 0;
        for (int i = 0; i < chapters.size(); i++) if (visible(i, d)) n++;
        return n;
    }

    /** O capítulo aparece na lista? (secreto e ainda bloqueado = some, nem "???") */
    public boolean visible(int index, MagicData d) {
        JournalChapter c = chapters.get(index);
        return !c.secret() || d.hasFlag(c.begunFlag()) || state(index, d) != State.LOCKED;
    }

    /** Passo concluído? (marcado pelo servidor, ou condição já cumprida esperando a próxima checagem). */
    public static boolean stepDone(JournalChapter c, JournalStep s, MagicData d, @Nullable Player player) {
        return d.hasFlag(s.flag(c)) || s.condition().done(d, player);
    }

    /** Próximo objetivo: o primeiro obrigatório pendente; senão o primeiro opcional pendente; senão null. */
    @Nullable
    public static JournalStep currentStep(JournalChapter c, MagicData d, @Nullable Player player) {
        JournalStep optional = null;
        for (JournalStep s : c.visibleSteps()) {
            if (stepDone(c, s, d, player)) continue;
            if (!s.optional()) return s;
            if (optional == null) optional = s;
        }
        return optional;
    }

    // =====================================================================================
    //  Leitura
    // =====================================================================================
    /** Junta os arquivos (cada um: {"chapters": [...]} ou uma lista) e ordena por "order". */
    static JournalBook fromFiles(Map<ResourceLocation, JsonElement> files) {
        List<ResourceLocation> ids = new ArrayList<>(files.keySet());
        Collections.sort(ids);
        JsonArray merged = new JsonArray();
        for (ResourceLocation id : ids) {
            JsonElement root = files.get(id);
            JsonArray arr = root.isJsonArray() ? root.getAsJsonArray()
                    : root.isJsonObject() && root.getAsJsonObject().has("chapters") ? root.getAsJsonObject().getAsJsonArray("chapters") : null;
            if (arr == null) {
                IsekaiMahou.LOGGER.warn("Diario: arquivo {} ignorado (esperado um objeto com a lista \"chapters\")", id);
                continue;
            }
            for (JsonElement e : arr) merged.add(e);
        }
        return parseArray(merged.toString());
    }

    /** Lê a lista de capítulos; capítulos inválidos são ignorados (com aviso no log). */
    static JournalBook parseArray(String json) {
        List<JournalChapter> main = new ArrayList<>();
        Map<String, List<JournalChapter>> tracks = new HashMap<>();
        JsonArray kept = new JsonArray();
        Set<String> seen = new HashSet<>();
        JsonArray arr;
        try {
            arr = JsonParser.parseString(json).getAsJsonArray();
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.error("Diario: JSON invalido", e);
            return EMPTY;
        }
        int index = 0;
        for (JsonElement e : arr) {
            index++;
            try {
                JsonObject o = e.getAsJsonObject();
                JournalChapter c = JournalChapter.parse(o, index * 10);
                if (!seen.add(c.id())) {
                    IsekaiMahou.LOGGER.warn("Diario: capitulo repetido ignorado: {}", c.id());
                    continue;
                }
                // Todos os passos obrigatórios são de módulos que não estão no jogo: o capítulo não existe aqui.
                if (c.required().isEmpty()) {
                    IsekaiMahou.LOGGER.info("Diario: capitulo {} sem passos disponiveis neste jogo (modulo ausente) — ignorado", c.id());
                    continue;
                }
                if (c.isClassRoute()) {
                    if (c.magicPath() == MagicPath.NONE) {
                        IsekaiMahou.LOGGER.warn("Diario: capitulo {} de uma classe que nao existe ({}) — ignorado", c.id(), c.path());
                        continue;
                    }
                    tracks.computeIfAbsent(c.path(), k -> new ArrayList<>()).add(c);
                } else {
                    main.add(c);
                }
                kept.add(o);
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.warn("Diario: capitulo ignorado ({}): {}", index, ex.getMessage());
            }
        }
        main.sort(Comparator.comparingInt(JournalChapter::order));
        Map<String, List<JournalChapter>> frozen = new HashMap<>();
        tracks.forEach((k, v) -> {
            v.sort(Comparator.comparingInt(JournalChapter::order));
            frozen.put(k, List.copyOf(v));
        });
        return new JournalBook(List.copyOf(main), Map.copyOf(frozen), kept.toString());
    }
}
