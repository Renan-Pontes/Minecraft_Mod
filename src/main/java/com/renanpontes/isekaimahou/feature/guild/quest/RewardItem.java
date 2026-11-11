package com.renanpontes.isekaimahou.feature.guild.quest;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Item de recompensa/loja por id em texto (resolvido na hora — itens de outros módulos podem não existir).
 * {@code {"id": "minecraft:potion", "count": 1, "potion": "minecraft:healing"}}
 */
public record RewardItem(ResourceLocation id, int count, Optional<ResourceLocation> potion) {
    public static final Codec<RewardItem> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(RewardItem::id),
            Codec.intRange(1, 6400).optionalFieldOf("count", 1).forGetter(RewardItem::count),
            ResourceLocation.CODEC.optionalFieldOf("potion").forGetter(RewardItem::potion)
    ).apply(i, RewardItem::new));

    /** Pilha pronta (pode passar do tamanho máximo: quem entrega divide). Vazia se o item não existir. */
    public ItemStack toStack() {
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, count);
        potion.flatMap(BuiltInRegistries.POTION::getHolder).ifPresent(p -> stack.set(DataComponents.POTION_CONTENTS, new PotionContents(p)));
        return stack;
    }
}
