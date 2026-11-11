package com.renanpontes.isekaimahou.feature.ritual_mage.research;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Atlas Arcano do jogador (attachment "mage_research"): fenômenos observados (com o tempo da última
 * observação, para o estudo diário) e teses concluídas.
 */
public class MageResearchData {
    public static final Codec<MageResearchData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("observed", Map.of()).forGetter(d -> d.observed),
            Codec.STRING.listOf().optionalFieldOf("theses", List.of()).forGetter(d -> new ArrayList<>(d.theses))
    ).apply(i, MageResearchData::new));

    /** id do fenômeno -> gameTime da última observação. */
    private final Map<String, Long> observed = new HashMap<>();
    private final Set<String> theses = new LinkedHashSet<>();

    public MageResearchData() {}

    private MageResearchData(Map<String, Long> observed, List<String> theses) {
        this.observed.putAll(observed);
        this.theses.addAll(theses);
    }

    public boolean isDiscovered(String id) { return observed.containsKey(id); }
    public long lastObserved(String id) { return observed.getOrDefault(id, Long.MIN_VALUE); }
    public void observe(String id, long gameTime) { observed.put(id, gameTime); }
    public Set<String> discovered() { return observed.keySet(); }

    public boolean hasThesis(String id) { return theses.contains(id); }
    public void completeThesis(String id) { theses.add(id); }
    public Set<String> theses() { return theses; }
    public int thesisCount() { return theses.size(); }

    public void reset() {
        observed.clear();
        theses.clear();
    }
}
