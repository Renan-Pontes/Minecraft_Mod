package com.renanpontes.isekaimahou.feature.structures_b.loot;

import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.structures_b.StructuresBContent;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * {@code isekai_mahou:item_if_present} — troca o item da entrada por {@code item} (normalmente de OUTRO módulo,
 * ex.: research_note, witch_recipe_page) somente se ele existir no registro; senão o item base da entrada fica
 * como substituto. Assim um módulo ausente nunca invalida a tabela de loot inteira.
 * {@code components} (opcional): patch de componentes aplicado ao item trocado; se não for válido, é ignorado.
 * Mantém a quantidade da pilha original, limitada ao tamanho de pilha do item trocado — use {@code set_count}
 * ANTES desta função: assim o substituto vanilla fica com a quantidade sorteada e um item não empilhável
 * (ex.: giz com durabilidade) sai sozinho em vez de virar várias cópias.
 */
public class ItemIfPresentFunction extends LootItemConditionalFunction {
    public static final MapCodec<ItemIfPresentFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
            .and(ResourceLocation.CODEC.fieldOf("item").forGetter(f -> f.item))
            .and(Codec.PASSTHROUGH.optionalFieldOf("components").forGetter(f -> f.components))
            .apply(i, ItemIfPresentFunction::new));

    private final ResourceLocation item;
    private final Optional<Dynamic<?>> components;

    private ItemIfPresentFunction(List<LootItemCondition> conditions, ResourceLocation item, Optional<Dynamic<?>> components) {
        super(conditions);
        this.item = item;
        this.components = components;
    }

    @Override
    public LootItemFunctionType<ItemIfPresentFunction> getType() {
        return StructuresBContent.ITEM_IF_PRESENT.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext ctx) {
        Optional<Item> target = BuiltInRegistries.ITEM.getOptional(item);
        if (target.isEmpty()) return stack;
        ItemStack out = new ItemStack(target.get(), Math.max(1, stack.getCount()));
        components.ifPresent(dyn -> {
            // Componentes de outros módulos só existem em tempo de execução: lê aqui, ignorando erros.
            JsonElement json = dyn.convert(JsonOps.INSTANCE).getValue();
            DataComponentPatch.CODEC.parse(ctx.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE), json)
                    .resultOrPartial(err -> IsekaiMahou.LOGGER.debug("item_if_present: componentes ignorados para {}: {}", item, err))
                    .ifPresent(out::applyComponents);
        });
        out.setCount(Math.min(out.getCount(), out.getMaxStackSize()));
        return out;
    }
}
