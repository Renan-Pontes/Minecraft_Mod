package com.renanpontes.isekaimahou.feature.ritual_necro.rite;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Ranks do Rito Ossuário carregados de data/&lt;ns&gt;/necro_rites (lado SERVIDOR). */
public final class NecroRites {
    private static final Pattern RANK_NAME = Pattern.compile("(?:.*/)?rank_(\\d+)$");
    private static volatile Map<Integer, NecroRite> rites = Map.of();

    /** Rito do rank informado (1..), ou null se não existir. */
    public static NecroRite get(int rank) {
        return rites.get(rank);
    }

    public static boolean isEmpty() {
        return rites.isEmpty();
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        private static final Gson GSON = new GsonBuilder().create();

        public Loader() {
            super(GSON, "necro_rites");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            Map<Integer, NecroRite> loaded = new TreeMap<>();
            files.forEach((id, json) -> {
                Matcher m = RANK_NAME.matcher(id.getPath());
                int fromName = m.matches() ? Integer.parseInt(m.group(1)) : 0;
                NecroRite.File.CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(err -> IsekaiMahou.LOGGER.warn("Rito Ossuario ignorado {}: {}", id, err))
                        .map(f -> f.resolve(id, fromName))
                        .ifPresent(rite -> {
                            if (rite.rank() <= 0) {
                                IsekaiMahou.LOGGER.warn("Rito Ossuario {} sem rank (use \"rank\" ou o nome rank_<n>)", id);
                            } else if (rite.souls().isEmpty()) {
                                IsekaiMahou.LOGGER.warn("Rito Ossuario {} sem nenhuma alma valida: ignorado", id);
                            } else if (loaded.putIfAbsent(rite.rank(), rite) != null) {
                                IsekaiMahou.LOGGER.warn("Rito Ossuario {}: rank {} repetido, ignorado", id, rite.rank());
                            }
                        });
            });
            rites = Collections.unmodifiableMap(loaded);
            IsekaiMahou.LOGGER.info("Rito Ossuario: {} ranks carregados", loaded.size());
        }
    }

    private NecroRites() {}
}
