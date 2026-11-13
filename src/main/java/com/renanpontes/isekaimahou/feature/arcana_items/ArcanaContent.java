package com.renanpontes.isekaimahou.feature.arcana_items;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.arcana_items.item.AlchemistBagItem;
import com.renanpontes.isekaimahou.feature.arcana_items.item.BlankRecipeScrollItem;
import com.renanpontes.isekaimahou.feature.arcana_items.item.ManaNecklaceItem;
import com.renanpontes.isekaimahou.feature.arcana_items.item.RecipeScrollItem;
import com.renanpontes.isekaimahou.feature.arcana_items.item.ReturnScrollItem;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registros do módulo: os itens, os componentes que eles guardam (mana do colar, autor do pergaminho, coleta da bolsa) e
 * os dados do jogador (última cidade visitada — destino do Pergaminho de Retorno).
 */
public final class ArcanaContent {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE,
            IsekaiMahou.MODID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,
            IsekaiMahou.MODID);

    /** Mana guardada no Colar de Mana (inteiro; a fração fica só na memória do servidor). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> STORED_MANA = COMPONENTS.registerComponentType(
            "arcana_items_stored_mana", b -> b.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** Quem escreveu o Pergaminho de Receita (só para o tooltip). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> AUTHOR = COMPONENTS.registerComponentType(
            "arcana_items_author", b -> b.persistent(Codec.string(0, 48)).networkSynchronized(ByteBufCodecs.stringUtf8(192)));
    /** Bolsa do Alquimista: coleta automática ligada? (ausente = ligada) */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BAG_COLLECT = COMPONENTS.registerComponentType(
            "arcana_items_bag_collect", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** Dados do jogador que sobrevivem à morte: {@code city} (id do lugar) e {@code city_dim}. */
    public static final Supplier<AttachmentType<CompoundTag>> PLAYER_DATA = ATTACHMENTS.register("arcana_items_player",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    // ------------------------------------------------------------------ Colar de Mana (3 níveis de capacidade)
    public static final DeferredItem<ManaNecklaceItem> MANA_NECKLACE = ITEMS.register("mana_necklace",
            () -> new ManaNecklaceItem(one(Rarity.UNCOMMON), 1, 400, 0.50F));
    public static final DeferredItem<ManaNecklaceItem> GREATER_MANA_NECKLACE = ITEMS.register("greater_mana_necklace",
            () -> new ManaNecklaceItem(one(Rarity.RARE), 2, 1200, 0.65F));
    public static final DeferredItem<ManaNecklaceItem> GRAND_MANA_NECKLACE = ITEMS.register("grand_mana_necklace",
            () -> new ManaNecklaceItem(one(Rarity.EPIC).fireResistant(), 3, 3000, 0.80F));

    // ------------------------------------------------------------------ Pergaminho de Receita
    public static final DeferredItem<BlankRecipeScrollItem> BLANK_RECIPE_SCROLL = ITEMS.register("blank_recipe_scroll",
            () -> new BlankRecipeScrollItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final DeferredItem<RecipeScrollItem> RECIPE_SCROLL = ITEMS.register("recipe_scroll",
            () -> new RecipeScrollItem(one(Rarity.RARE)));

    // ------------------------------------------------------------------ Bolsa do Alquimista / Pergaminho de Retorno
    public static final DeferredItem<AlchemistBagItem> ALCHEMIST_BAG = ITEMS.register("alchemist_bag",
            () -> new AlchemistBagItem(one(Rarity.UNCOMMON).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY)));
    public static final DeferredItem<ReturnScrollItem> RETURN_SCROLL = ITEMS.register("return_scroll",
            () -> new ReturnScrollItem(new Item.Properties().stacksTo(16)));

    private static Item.Properties one(Rarity rarity) {
        return new Item.Properties().stacksTo(1).rarity(rarity);
    }

    static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        ATTACHMENTS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(ArcanaContent::fillTab);
    }

    private static void fillTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CoreRegistries.TAB_MAGIC.getKey()) return;
        event.accept(MANA_NECKLACE.get());
        event.accept(GREATER_MANA_NECKLACE.get());
        event.accept(GRAND_MANA_NECKLACE.get());
        event.accept(BLANK_RECIPE_SCROLL.get());
        event.accept(ALCHEMIST_BAG.get());
        event.accept(RETURN_SCROLL.get());
    }

    private ArcanaContent() {}
}
