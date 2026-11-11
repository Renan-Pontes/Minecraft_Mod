package com.renanpontes.isekaimahou.core.api;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Famílias de criaturas para missões de abate: "derrote goblins" vale para TODAS as variantes do mod (goblin com lobo,
 * arqueiro, xamã, hobgoblin, rei...), "derrote slimes" para slime de fogo/água/pedra/vento, geleia venenosa etc.
 * <p>Regra: para um alvo {@code isekai_mahou:<p>}, entram as criaturas do mod cujo id começa com {@code <p>_},
 * termina com {@code _<p>} ou termina com {@code <p>} (hobgoblin), mais os apelidos de {@link #ALIASES}.
 */
public final class KillFamilies {
    private static final Map<String, List<String>> ALIASES = Map.of("slime", List.of("poison_jelly"));
    private static final Map<List<ResourceLocation>, List<ResourceLocation>> CACHE = new ConcurrentHashMap<>();

    /** Os alvos + as variantes da família de cada um (sem repetir, alvos primeiro). */
    public static List<ResourceLocation> expand(List<ResourceLocation> targets) {
        return CACHE.computeIfAbsent(List.copyOf(targets), KillFamilies::compute);
    }

    private static List<ResourceLocation> compute(List<ResourceLocation> targets) {
        Set<ResourceLocation> out = new LinkedHashSet<>(targets);
        for (ResourceLocation t : targets) {
            if (!IsekaiMahou.MODID.equals(t.getNamespace())) continue;
            String p = t.getPath();
            for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                if (!IsekaiMahou.MODID.equals(id.getNamespace())) continue;
                String q = id.getPath();
                if (q.startsWith(p + "_") || q.endsWith("_" + p) || (q.endsWith(p) && q.length() > p.length())) out.add(id);
            }
            for (String a : ALIASES.getOrDefault(p, List.of())) {
                ResourceLocation id = IsekaiMahou.id(a);
                if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) out.add(id);
            }
        }
        return List.copyOf(new ArrayList<>(out));
    }

    private KillFamilies() {}
}
