package com.renanpontes.isekaimahou.feature.structures_b.world;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.mojang.serialization.Codec;

/**
 * Attachment do jogador: instâncias de estruturas já visitadas ("dimensão|estrutura|chunk").
 * Guarda no máximo {@link #LIMIT} (as mais antigas saem primeiro) — só serve para não repetir a mensagem.
 */
public class ExploredStructures {
    public static final int LIMIT = 512;
    public static final Codec<ExploredStructures> CODEC = Codec.STRING.listOf()
            .xmap(ExploredStructures::new, d -> new ArrayList<>(d.visited));

    private final LinkedHashSet<String> visited = new LinkedHashSet<>();

    public ExploredStructures() {}

    private ExploredStructures(List<String> list) {
        visited.addAll(list);
    }

    /** Marca a instância como visitada. Retorna false se já estava. */
    public boolean add(String instance) {
        if (!visited.add(instance)) return false;
        while (visited.size() > LIMIT) visited.remove(visited.iterator().next());
        return true;
    }
}
