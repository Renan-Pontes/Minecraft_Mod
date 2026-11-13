package com.renanpontes.isekaimahou.feature.talents;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * As árvores do SERVIDOR, lidas dos datapacks ({@code data/<ns>/talents/<id da classe>.json}; outro namespace com o
 * mesmo nome substitui a do mod). Cada recarga sobe a {@link #version()} — é assim que o módulo sabe que precisa reenviar
 * a árvore e reaplicar os efeitos. O cliente NÃO usa esta classe: recebe o JSON da classe dele pelo pacote de sincronia.
 */
public final class TalentTrees extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static volatile Map<String, TalentTree> trees = Map.of();
    private static volatile Map<String, String> raw = Map.of();
    private static volatile int version;

    TalentTrees() {
        super(GSON, "talents");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, TalentTree> t = new HashMap<>();
        Map<String, String> r = new HashMap<>();
        // Primeiro as do mod, depois as de outros namespaces (que substituem)
        for (int pass = 0; pass < 2; pass++) {
            for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
                boolean own = IsekaiMahou.MODID.equals(e.getKey().getNamespace());
                if (own != (pass == 0) || !e.getValue().isJsonObject()) continue;
                String cls = e.getKey().getPath();
                try {
                    t.put(cls, TalentTree.parse(cls, e.getValue().getAsJsonObject()));
                    r.put(cls, GSON.toJson(e.getValue()));
                } catch (RuntimeException ex) {
                    IsekaiMahou.LOGGER.error("Árvore de talentos inválida: {}", e.getKey(), ex);
                }
            }
        }
        trees = Map.copyOf(t);
        raw = Map.copyOf(r);
        version++;
    }

    /** Árvore da classe (id de {@code MagicPath}), ou null se a classe não tem árvore. */
    @Nullable
    public static TalentTree get(String classId) {
        return trees.get(classId);
    }

    /** JSON da árvore (o que vai para o cliente); "" se não houver. */
    public static String json(String classId) {
        return raw.getOrDefault(classId, "");
    }

    public static Set<String> classes() {
        return trees.keySet();
    }

    public static int version() {
        return version;
    }
}
