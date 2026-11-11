package com.renanpontes.isekaimahou.feature.ritual_mage.network;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

/**
 * Cópia do Atlas no cliente (telas do Atlas e da Mesa). Sem imports de cliente: existe com segurança
 * no servidor dedicado, onde simplesmente nunca recebe dados.
 */
public final class ClientResearch {
    private static List<MagePayloads.PhenomenonInfo> phenomena = List.of();
    private static Map<ResourceLocation, MagePayloads.PhenomenonInfo> byId = Map.of();
    private static Set<String> discovered = Set.of();
    private static Set<String> theses = Set.of();
    private static boolean atlasReceived;

    /** Momento (ms) da última tese concluída recebida — a tela da Mesa mostra um aviso. */
    public static long thesisCompletedAt;

    static void applyPhenomena(MagePayloads.PhenomenaSync p) {
        phenomena = List.copyOf(p.list());
        Map<ResourceLocation, MagePayloads.PhenomenonInfo> map = new LinkedHashMap<>();
        for (MagePayloads.PhenomenonInfo i : phenomena) map.put(i.id(), i);
        byId = map;
    }

    static void applyAtlas(MagePayloads.AtlasSync p) {
        Set<String> newTheses = new HashSet<>(p.theses());
        if (atlasReceived && newTheses.size() > theses.size()) thesisCompletedAt = Util.getMillis();
        discovered = new HashSet<>(p.discovered());
        theses = newTheses;
        atlasReceived = true;
    }

    public static List<MagePayloads.PhenomenonInfo> phenomena() { return phenomena; }
    public static MagePayloads.PhenomenonInfo get(ResourceLocation id) { return byId.get(id); }
    public static boolean isDiscovered(ResourceLocation id) { return discovered.contains(id.toString()); }
    public static boolean hasThesis(String id) { return theses.contains(id); }
    public static int thesisCount() { return theses.size(); }

    /** Quantos dos fenômenos disponíveis já foram observados. */
    public static int discoveredCount() {
        int n = 0;
        for (MagePayloads.PhenomenonInfo i : phenomena) if (discovered.contains(i.id().toString())) n++;
        return n;
    }

    public static void reset() {
        phenomena = List.of();
        byId = Map.of();
        discovered = Set.of();
        theses = Set.of();
        atlasReceived = false;
        thesisCompletedAt = 0;
    }

    private ClientResearch() {}
}
