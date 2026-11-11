package com.renanpontes.isekaimahou.feature.outfits;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/**
 * "Reforma" de roupa: peça de traje + tecido + item temático vira a mesma peça (do MESMO grau) de outra classe.
 * Diferente de uma receita sem forma comum, a peça nova herda desgaste, encantamentos e nome da antiga
 * (o tecido encantável é o atrativo do traje; perder tudo ao trocar de classe seria uma armadilha, e consertar
 * de graça seria um atalho). Reformar para a própria classe não é aceito. Entre categorias (manto mágico -> armadura
 * guerreira) a durabilidade máxima muda, então o desgaste é convertido em proporção.
 * JSON igual ao de {@code minecraft:crafting_shapeless}, com {@code "type": "isekai_mahou:outfit_retailor"}.
 */
public class OutfitRetailorRecipe extends ShapelessRecipe {
    public static final MapCodec<OutfitRetailorRecipe> CODEC =
            RecipeSerializer.SHAPELESS_RECIPE.codec().xmap(OutfitRetailorRecipe::of, r -> r);
    public static final StreamCodec<RegistryFriendlyByteBuf, OutfitRetailorRecipe> STREAM_CODEC =
            RecipeSerializer.SHAPELESS_RECIPE.streamCodec().map(OutfitRetailorRecipe::of, r -> r);

    public OutfitRetailorRecipe(String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients) {
        super(group, category, result, ingredients);
    }

    private static OutfitRetailorRecipe of(ShapelessRecipe base) {
        // a receita sem forma devolve o resultado sem consultar registros
        return new OutfitRetailorRecipe(base.getGroup(), base.category(), base.getResultItem(RegistryAccess.EMPTY),
                base.getIngredients());
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (!super.matches(input, level)) return false;
        ItemStack source = source(input);
        return !source.isEmpty() && !source.is(getResultItem(level.registryAccess()).getItem());
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = getResultItem(registries);
        ItemStack source = source(input);
        if (source.isEmpty()) return result.copy();
        ItemStack out = source.transmuteCopy(result.getItem(), 1);
        // a durabilidade muda com a categoria (tecido mágico, metal guerreiro, couro à distância): o desgaste passa
        // em PROPORÇÃO, senão uma armadura gasta viraria uma peça quebrando (ou ganharia conserto de graça)
        int srcMax = source.getMaxDamage(), outMax = out.getMaxDamage();
        if (srcMax > 0 && outMax > 0 && srcMax != outMax) {
            int damage = Math.round(source.getDamageValue() / (float) srcMax * outMax);
            out.setDamageValue(Math.min(damage, outMax - 1));
        }
        return out;
    }

    /** A peça de traje que está sendo reformada (a única roupa de classe na grade). */
    private static ItemStack source(CraftingInput input) {
        for (ItemStack stack : input.items()) {
            if (stack.getItem() instanceof OutfitArmorItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return OutfitRegistries.RETAILOR.get();
    }
}
