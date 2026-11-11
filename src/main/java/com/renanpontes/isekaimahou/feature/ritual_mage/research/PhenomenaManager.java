package com.renanpontes.isekaimahou.feature.ritual_mage.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ritual_mage.network.MagePayloads;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Carrega os fenômenos de data/<ns>/mage_phenomena/*.json (recarrega com /reload) e monta a lista enviada ao cliente.
 */
public final class PhenomenaManager extends SimpleJsonResourceReloadListener {
    public static final PhenomenaManager INSTANCE = new PhenomenaManager();

    private static volatile Map<ResourceLocation, Phenomenon> byId = Map.of();
    private static volatile List<Phenomenon> sorted = List.of();

    private PhenomenaManager() {
        super(new Gson(), "mage_phenomena");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<Phenomenon> list = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                list.add(Phenomenon.parse(id, json.getAsJsonObject()));
            } catch (RuntimeException e) {
                IsekaiMahou.LOGGER.error("Fenômeno de pesquisa inválido: {}", id, e);
            }
        });
        list.sort(Comparator.<Phenomenon>comparingInt(p -> Phenomenon.CATEGORIES.indexOf(p.category))
                .thenComparingInt(p -> p.order).thenComparing(p -> p.id.toString()));
        Map<ResourceLocation, Phenomenon> map = new LinkedHashMap<>();
        for (Phenomenon p : list) map.put(p.id, p);
        byId = map;
        sorted = List.copyOf(list);
        IsekaiMahou.LOGGER.info("Pesquisa Arcana: {} fenômenos carregados", list.size());
    }

    @Nullable
    public static Phenomenon get(ResourceLocation id) {
        return byId.get(id);
    }

    public static List<Phenomenon> all() {
        return sorted;
    }

    /** Fenômenos que existem neste mundo (módulos/dimensões presentes). */
    public static List<Phenomenon> available(MinecraftServer server) {
        List<Phenomenon> out = new ArrayList<>();
        for (Phenomenon p : sorted) if (p.available(server)) out.add(p);
        return out;
    }

    /** Pacote com as definições visíveis no Atlas. */
    public static MagePayloads.PhenomenaSync syncPayload(MinecraftServer server) {
        List<MagePayloads.PhenomenonInfo> infos = new ArrayList<>();
        for (Phenomenon p : available(server)) {
            infos.add(new MagePayloads.PhenomenonInfo(p.id, p.category, p.elements.stream().map(e -> e.id).toList(),
                    p.rarity.ordinal(), p.icons));
        }
        return new MagePayloads.PhenomenaSync(infos);
    }
}
