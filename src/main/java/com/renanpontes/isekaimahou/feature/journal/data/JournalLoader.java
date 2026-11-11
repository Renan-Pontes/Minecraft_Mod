package com.renanpontes.isekaimahou.feature.journal.data;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Lê os capítulos de {@code data/<ns>/journal/*.json} (recarrega com /reload). */
public final class JournalLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public JournalLoader() {
        super(GSON, "journal");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        JournalBook book = JournalBook.fromFiles(files);
        JournalBook.setServer(book);
        IsekaiMahou.LOGGER.info("Diario do Isekai: {} capitulos carregados", book.chapters().size());
    }
}
