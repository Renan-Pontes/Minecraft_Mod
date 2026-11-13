package com.renanpontes.isekaimahou.feature.journal.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.renanpontes.isekaimahou.core.MagicPath;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Capítulo do Diário do Isekai (data-driven: {@code data/<ns>/journal/*.json}).
 * Textos: {@code journal.isekai_mahou.<id>.title}, {@code .lore} (abertura) e {@code .outro} (desfecho).
 *
 * @param icons  itens mostrados na lista (se houver vários, a aba alterna entre eles)
 * @param color  cor RGB do capítulo (título, Espírito Guia)
 * @param finale   último capítulo: o desfecho é o final do mod (cutscene + aviso a todos)
 * @param epilogue nº de linhas do epílogo narrado ({@code journal.isekai_mahou.<id>.epilogue.<n>}, 1..n), mostrado no final
 * @param intro    o capítulo tem a fala curta do Espírito Guia na abertura ({@code journal.isekai_mahou.<id>.intro},
 *                 caixa de diálogo); sem ela, o espírito usa uma saudação genérica
 * @param path     v1.0: id da classe ({@code "smith"}, {@code "mage"}...) — o capítulo é da ROTA DA CLASSE (trilha paralela
 *                 à história, só para quem é daquela classe); null = história principal
 * @param secret   v1.0: escondido na lista do Diário (nem "???") até começar — o capítulo de depois do Dragão (desde a
 *                 onda 5, o Abismo Infinito: "o Dragão é o fim", nada aparece depois dele antes de ele cair)
 */
public record JournalChapter(String id, int order, List<ResourceLocation> icons, int color, List<JournalStep> steps,
                             JournalRewards rewards, boolean finale, int epilogue, boolean intro, @Nullable String path,
                             boolean secret) {

    public static JournalChapter parse(JsonObject o, int fallbackOrder) {
        String id = GsonHelper.getAsString(o, "id");
        if (!id.matches("[a-z0-9_]+")) throw new JsonParseException("id de capitulo invalido: " + id);
        List<ResourceLocation> icons = new ArrayList<>();
        if (o.has("icon")) {
            JsonElement ic = o.get("icon");
            if (ic.isJsonArray()) {
                for (JsonElement e : ic.getAsJsonArray()) icons.add(ResourceLocation.parse(e.getAsString()));
            } else {
                icons.add(ResourceLocation.parse(ic.getAsString()));
            }
        }
        List<JournalStep> steps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(o, "steps")) {
            JournalStep s = JournalStep.parse(e.getAsJsonObject());
            if (!seen.add(s.id())) throw new JsonParseException("passo repetido: " + s.id());
            steps.add(s);
        }
        if (steps.stream().allMatch(JournalStep::optional)) throw new JsonParseException("capitulo sem passos obrigatorios: " + id);
        JournalRewards rewards = o.has("rewards") ? JournalRewards.parse(GsonHelper.getAsJsonObject(o, "rewards")) : JournalRewards.NONE;
        String path = o.has("path") ? GsonHelper.getAsString(o, "path") : null;
        if (path != null && !path.matches("[a-z0-9_]+")) throw new JsonParseException("classe invalida: " + path);
        boolean finale = GsonHelper.getAsBoolean(o, "finale", false);
        // A rota de classe corre ao lado da história: não pode ter o final do mod.
        if (path != null && finale) throw new JsonParseException("capitulo de classe nao pode ser o final: " + id);
        return new JournalChapter(id, GsonHelper.getAsInt(o, "order", fallbackOrder), List.copyOf(icons),
                parseColor(GsonHelper.getAsString(o, "color", "#B98CFF")), List.copyOf(steps), rewards,
                finale, Math.max(0, Math.min(20, GsonHelper.getAsInt(o, "epilogue", 0))),
                GsonHelper.getAsBoolean(o, "intro", false), path, path == null && GsonHelper.getAsBoolean(o, "secret", false));
    }

    /** Capítulo da rota de uma classe (trilha paralela)? */
    public boolean isClassRoute() {
        return path != null;
    }

    /** Classe da rota (NONE para a história principal ou classe que não existe mais). */
    public MagicPath magicPath() {
        return path == null ? MagicPath.NONE : MagicPath.byId(path);
    }

    private static int parseColor(String s) {
        try {
            return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            throw new JsonParseException("cor invalida: " + s);
        }
    }

    /** Marcador de capítulo concluído (convenção do contrato: journal.&lt;capitulo&gt;). */
    public String doneFlag() {
        return "journal." + id;
    }

    /** Marcador de capítulo iniciado (abertura já mostrada e Espírito Guia já apareceu). */
    public String begunFlag() {
        return "journal." + id + ".begun";
    }

    public String key() {
        return "journal.isekai_mahou." + id;
    }

    public MutableComponent title() {
        return Component.translatable(key() + ".title");
    }

    public MutableComponent lore() {
        return Component.translatable(key() + ".lore");
    }

    public MutableComponent outro() {
        return Component.translatable(key() + ".outro");
    }

    /** Fala curta do Espírito Guia ao abrir o capítulo (só quando {@link #intro()}). */
    public MutableComponent introLine() {
        return Component.translatable(key() + ".intro");
    }

    public MutableComponent epilogueLine(int n) {
        return Component.translatable(key() + ".epilogue." + n);
    }

    /** Passos que existem neste jogo (sem os de módulos ausentes), na ordem do JSON. */
    public List<JournalStep> visibleSteps() {
        return steps.stream().filter(JournalStep::available).toList();
    }

    /** Passos obrigatórios que existem neste jogo: concluir todos fecha o capítulo. */
    public List<JournalStep> required() {
        return steps.stream().filter(s -> !s.optional() && s.available()).toList();
    }
}
