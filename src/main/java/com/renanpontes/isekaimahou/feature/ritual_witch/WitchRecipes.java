package com.renanpontes.isekaimahou.feature.ritual_witch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

/**
 * Registro das receitas do caldeirão (carregadas de data/&lt;ns&gt;/witch_recipes). Lado SERVIDOR.
 * O cliente só recebe o conjunto de itens que são ingredientes ({@link #clientIngredients}) para prever
 * o clique no caldeirão, e as receitas que o jogador conhece quando abre o Livro de Receitas.
 */
public final class WitchRecipes {
    private static volatile Map<ResourceLocation, WitchRecipe> recipes = Map.of();
    private static volatile Set<Item> ingredients = Set.of();

    /** Cópia no cliente (recebida por pacote): itens aceitos pelo caldeirão. */
    public static volatile Set<Item> clientIngredients = Set.of();

    public static Collection<WitchRecipe> all() {
        return recipes.values();
    }

    public static WitchRecipe get(ResourceLocation id) {
        return id == null ? null : recipes.get(id);
    }

    public static boolean isIngredient(Item item, boolean clientSide) {
        return (clientSide ? clientIngredients : ingredients).contains(item);
    }

    public static Set<Item> ingredients() {
        return ingredients;
    }

    /** Receitas cuja sequência começa com {@code current}. */
    public static List<WitchRecipe> candidates(List<Item> current) {
        List<WitchRecipe> out = new ArrayList<>();
        for (WitchRecipe r : recipes.values()) if (r.startsWith(current)) out.add(r);
        return out;
    }

    public static WitchRecipe exact(List<Item> current) {
        for (WitchRecipe r : recipes.values()) if (r.matchesExactly(current)) return r;
        return null;
    }

    /** Leitor de recursos registrado no AddReloadListenerEvent. */
    public static final class Loader extends SimpleJsonResourceReloadListener {
        private static final Gson GSON = new GsonBuilder().create();
        private final HolderLookup.Provider registries;

        public Loader(HolderLookup.Provider registries) {
            super(GSON, "witch_recipes");
            this.registries = registries;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            Map<ResourceLocation, WitchRecipe> loaded = new LinkedHashMap<>();
            Set<Item> ing = new HashSet<>();
            List<ResourceLocation> ids = new ArrayList<>(files.keySet());
            Collections.sort(ids);
            recipes:
            for (ResourceLocation id : ids) {
                // Sem resultado parcial: um ingrediente com id desconhecido seria descartado em silêncio e mudaria a sequência
                DataResult<WitchRecipe> parsed = WitchRecipe.FILE_CODEC.parse(ops, files.get(id));
                if (parsed.error().isPresent() || parsed.result().isEmpty()) {
                    IsekaiMahou.LOGGER.warn("Receita de bruxa ignorada {}: {}", id,
                            parsed.error().map(DataResult.Error::message).orElse("?"));
                    continue;
                }
                WitchRecipe r = parsed.result().get();
                if (r.ingredients().isEmpty() || r.ingredients().size() > WitchRecipe.MAX_INGREDIENTS || r.heat() == WitchHeat.NONE
                        || r.result().isEmpty()) {
                    IsekaiMahou.LOGGER.warn("Receita de bruxa invalida {} (ingredientes 1..{}, calor e resultado obrigatorios)",
                            id, WitchRecipe.MAX_INGREDIENTS);
                    continue;
                }
                for (WitchRecipe other : loaded.values()) {
                    if (other.ingredients().equals(r.ingredients())) {
                        IsekaiMahou.LOGGER.warn("Receita de bruxa {} tem a mesma sequencia de {}: ignorada", id, other.id());
                        continue recipes;
                    }
                }
                loaded.put(id, r.withId(id));
                ing.addAll(r.ingredients());
            }
            recipes = Collections.unmodifiableMap(loaded);
            ingredients = Collections.unmodifiableSet(ing);
            IsekaiMahou.LOGGER.info("Caldeirao Ritual: {} receitas carregadas", loaded.size());
        }
    }

    /** Lista de ids dos ingredientes (para o pacote de sincronização). */
    public static List<ResourceLocation> ingredientIds() {
        List<ResourceLocation> out = new ArrayList<>();
        for (Item i : ingredients) out.add(BuiltInRegistries.ITEM.getKey(i));
        return out;
    }

    private WitchRecipes() {}
}
