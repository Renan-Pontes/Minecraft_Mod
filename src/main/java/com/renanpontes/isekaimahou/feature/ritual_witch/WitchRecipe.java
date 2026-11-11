package com.renanpontes.isekaimahou.feature.ritual_witch;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.ritual_witch.item.RevelationPotionItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Receita do Caldeirão Ritual (data/&lt;ns&gt;/witch_recipes/&lt;id&gt;.json).
 * <pre>
 * { "ingredients": ["isekai_mahou:witch_mandrake_root", "minecraft:apple"],   // NA ORDEM
 *   "stirs": 2, "heat": "low|medium|high",
 *   "moon_phases": [0,1,...7],   // opcional (padrão: todas). 0 = lua cheia, 4 = lua nova
 *   "night": false,             // opcional: só conclui à noite
 *   "result": {"id": "...", "count": 3}, "xp": 8, "color": "#E0465A", "element": "water" }
 * </pre>
 */
public record WitchRecipe(ResourceLocation id, List<Item> ingredients, int stirs, WitchHeat heat, int moonMask, boolean night,
                          ItemStack result, int xp, int color, Optional<Element> element) {

    public static final int ALL_MOONS = 0xFF;
    public static final int MAX_INGREDIENTS = 8;
    public static final int MAX_STIRS = 16;

    private static final Codec<Integer> COLOR = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "Cor invalida: " + s);
        }
    }, c -> String.format("#%06X", c));

    private static final Codec<Integer> MOONS = Codec.intRange(0, 7).listOf().xmap(list -> {
        int mask = 0;
        for (int p : list) mask |= 1 << p;
        return mask == 0 ? ALL_MOONS : mask;
    }, mask -> {
        List<Integer> out = new java.util.ArrayList<>();
        for (int p = 0; p < 8; p++) if ((mask & (1 << p)) != 0) out.add(p);
        return out;
    });

    /** Codec do arquivo (o id vem do nome do arquivo). */
    public static final Codec<WitchRecipe> FILE_CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().listOf().fieldOf("ingredients").forGetter(WitchRecipe::ingredients),
            Codec.intRange(1, MAX_STIRS).fieldOf("stirs").forGetter(WitchRecipe::stirs),
            WitchHeat.CODEC.fieldOf("heat").forGetter(WitchRecipe::heat),
            MOONS.optionalFieldOf("moon_phases", ALL_MOONS).forGetter(WitchRecipe::moonMask),
            Codec.BOOL.optionalFieldOf("night", false).forGetter(WitchRecipe::night),
            ItemStack.CODEC.fieldOf("result").forGetter(WitchRecipe::result),
            Codec.intRange(0, 100000).optionalFieldOf("xp", 5).forGetter(WitchRecipe::xp),
            COLOR.optionalFieldOf("color", 0x7A3FB0).forGetter(WitchRecipe::color),
            Element.CODEC.optionalFieldOf("element").forGetter(WitchRecipe::element)
    ).apply(i, (ing, stirs, heat, moons, night, result, xp, color, element) ->
            new WitchRecipe(null, List.copyOf(ing), stirs, heat, moons, night, result, xp, color, element)));

    public WitchRecipe withId(ResourceLocation newId) {
        return new WitchRecipe(newId, ingredients, stirs, heat, moonMask, night, result, xp, color, element);
    }

    public boolean moonAllowed(int phase) {
        return (moonMask & (1 << (phase & 7))) != 0;
    }

    /** A sequência atual (na ordem) é o começo desta receita? */
    public boolean startsWith(List<Item> current) {
        if (current.size() > ingredients.size()) return false;
        for (int i = 0; i < current.size(); i++) if (ingredients.get(i) != current.get(i)) return false;
        return true;
    }

    public boolean matchesExactly(List<Item> current) {
        return current.size() == ingredients.size() && startsWith(current);
    }

    /** Tier de Revelação (1..5) se o resultado for uma Poção de Revelação; 0 caso contrário. */
    public int revelationTier() {
        return result.getItem() instanceof RevelationPotionItem r ? r.tier() : 0;
    }

    /** Chave do marcador em MagicData que indica que o jogador conhece a receita. */
    public static String knowledgeFlag(ResourceLocation id) {
        return "witch_recipe." + id;
    }

    /** Nome da receita: tradução "witch_recipe.&lt;ns&gt;.&lt;path&gt;" ou o caminho formatado. */
    public static MutableComponent displayName(ResourceLocation id) {
        String path = id.getPath();
        String pretty = Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
        return Component.translatableWithFallback("witch_recipe." + id.getNamespace() + "." + path, pretty);
    }

    public static MutableComponent description(ResourceLocation id) {
        return Component.translatableWithFallback("witch_recipe." + id.getNamespace() + "." + id.getPath() + ".desc", "");
    }
}
