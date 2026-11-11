package com.renanpontes.isekaimahou.core.reputation;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Lê as facções de {@code data/<ns>/factions/*.json} (recarrega com /reload; o envio ao cliente é no OnDatapackSyncEvent). */
final class FactionLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    FactionLoader() {
        super(GSON, "factions");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        FactionRegistry.Snapshot snapshot = FactionRegistry.fromFiles(files);
        FactionRegistry.setServer(snapshot);
        IsekaiMahou.LOGGER.info("Reputação: {} facções carregadas", snapshot.sorted().size());
    }
}
