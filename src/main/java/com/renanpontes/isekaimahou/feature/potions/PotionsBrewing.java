package com.renanpontes.isekaimahou.feature.potions;

import java.util.function.Predicate;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * Alquimia no Suporte de Poções (o caminho "de laboratório"):
 * <pre>
 * Poção Estranha + Bagas Doces        → Vida Menor      | + Lápis-Lazúli          → Mana Menor
 * Menor  + Fatia de Melancia Reluzente → Comum          | + Fragmento de Ametista → Comum
 * Comum  + Essência da Luz             → Maior          | + Essência Arcana       → Maior
 * Maior  + Lágrima de Ghast            → Superior       | + Cristal de Mana       → Superior
 * qualquer frasco de beber + Pólvora → Arremessável;  + Redstone → Prolongada (mesmo tier)
 * </pre>
 * Suprema e Elixir Lendário só na bancada (essências + itens de chefe). O Caldeirão da Bruxa tem receitas em lote
 * (data/isekai_mahou/witch_recipes/potions_*.json).
 */
public final class PotionsBrewing {
    private PotionsBrewing() {}

    /** Receita item→item com predicados (o input fica nos 3 slots de baixo; o reagente, no de cima). */
    private record Mix(Predicate<ItemStack> input, Predicate<ItemStack> reagent, Supplier<ItemStack> output)
            implements IBrewingRecipe {
        @Override
        public boolean isInput(ItemStack stack) {
            return input.test(stack);
        }

        @Override
        public boolean isIngredient(ItemStack stack) {
            return reagent.test(stack);
        }

        @Override
        public ItemStack getOutput(ItemStack in, ItemStack ingredient) {
            return input.test(in) && reagent.test(ingredient) ? output.get() : ItemStack.EMPTY;
        }
    }

    public static void register(RegisterBrewingRecipesEvent event) {
        var b = event.getBuilder();
        Predicate<ItemStack> awkward = s -> s.is(Items.POTION)
                && s.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.AWKWARD);

        b.addRecipe(new Mix(awkward, is(Items.SWEET_BERRIES), out(PotionKind.HEALTH, PotionForm.DRINK, PotionTier.LESSER)));
        b.addRecipe(new Mix(awkward, is(Items.LAPIS_LAZULI), out(PotionKind.MANA, PotionForm.DRINK, PotionTier.LESSER)));

        upgrade(b, PotionKind.HEALTH, PotionTier.LESSER, () -> Items.GLISTERING_MELON_SLICE);
        upgrade(b, PotionKind.HEALTH, PotionTier.COMMON, () -> CoreRegistries.ESSENCES.get(Element.LIGHT).get());
        upgrade(b, PotionKind.HEALTH, PotionTier.GREATER, () -> Items.GHAST_TEAR);
        upgrade(b, PotionKind.MANA, PotionTier.LESSER, () -> Items.AMETHYST_SHARD);
        upgrade(b, PotionKind.MANA, PotionTier.COMMON, () -> CoreRegistries.ESSENCES.get(Element.ARCANE).get());
        upgrade(b, PotionKind.MANA, PotionTier.GREATER, () -> CoreRegistries.MANA_CRYSTAL.get());

        for (PotionKind kind : PotionKind.values()) {
            for (PotionTier tier : PotionTier.values()) {
                Predicate<ItemStack> base = is(() -> PotionsContent.item(kind, PotionForm.DRINK, tier).get());
                b.addRecipe(new Mix(base, is(Items.GUNPOWDER), out(kind, PotionForm.SPLASH, tier)));
                b.addRecipe(new Mix(base, is(Items.REDSTONE), out(kind, PotionForm.LONG, tier)));
            }
        }
    }

    private static void upgrade(net.minecraft.world.item.alchemy.PotionBrewing.Builder b, PotionKind kind, PotionTier from,
                                Supplier<Item> reagent) {
        PotionTier to = PotionTier.values()[from.ordinal() + 1];
        b.addRecipe(new Mix(is(() -> PotionsContent.item(kind, PotionForm.DRINK, from).get()), is(reagent),
                out(kind, PotionForm.DRINK, to)));
    }

    private static Predicate<ItemStack> is(Item item) {
        return s -> s.is(item);
    }

    /** Resolve o item na hora do teste (os registros podem ser lidos depois do evento). */
    private static Predicate<ItemStack> is(Supplier<Item> item) {
        return s -> !s.isEmpty() && s.is(item.get());
    }

    private static Supplier<ItemStack> out(PotionKind kind, PotionForm form, PotionTier tier) {
        return () -> new ItemStack(PotionsContent.item(kind, form, tier).get());
    }
}
