package com.renanpontes.isekaimahou.feature.ritual_necro;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ritual_necro.block.OssuaryAltarBlock;
import com.renanpontes.isekaimahou.feature.ritual_necro.block.OssuaryAltarBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_necro.item.BoneScytheItem;
import com.renanpontes.isekaimahou.feature.ritual_necro.item.PhylacteryItem;
import com.renanpontes.isekaimahou.feature.ritual_necro.soul.SoulStore;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do Ritual do Necromante: Altar Ossuário, Filactério, Foice de Ossos e o componente de almas. */
public final class NecroContent {
    private static final String MODID = IsekaiMahou.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    /** Mobs que não têm alma para colher (invocações, servos, ilusões). */
    public static final TagKey<EntityType<?>> SOULLESS = TagKey.create(Registries.ENTITY_TYPE, IsekaiMahou.id("necro_soulless"));

    // ---------------- Componente: almas no Filactério ----------------
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SoulStore>> SOULS =
            COMPONENTS.registerComponentType("necro_souls",
                    b -> b.persistent(SoulStore.CODEC).networkSynchronized(SoulStore.STREAM_CODEC).cacheEncoding());

    // ---------------- Bloco: Altar Ossuário ----------------
    public static final DeferredBlock<OssuaryAltarBlock> ALTAR = BLOCKS.registerBlock("ossuary_altar", OssuaryAltarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.XYLOPHONE)
                    .requiresCorrectToolForDrops().strength(3.0F, 9.0F).sound(SoundType.BONE_BLOCK).noOcclusion()
                    .lightLevel(s -> s.getValue(OssuaryAltarBlock.LIT) ? 9 : 2));
    public static final DeferredItem<BlockItem> ALTAR_ITEM = ITEMS.register("ossuary_altar",
            () -> new BlockItem(ALTAR.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<OssuaryAltarBlockEntity>> ALTAR_BE = BLOCK_ENTITIES.register("ossuary_altar",
            () -> BlockEntityType.Builder.of(OssuaryAltarBlockEntity::new, ALTAR.get()).build(null));

    // ---------------- Itens ----------------
    public static final DeferredItem<PhylacteryItem> PHYLACTERY = ITEMS.register("phylactery",
            () -> new PhylacteryItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant()));
    public static final DeferredItem<BoneScytheItem> BONE_SCYTHE = ITEMS.register("bone_scythe",
            () -> new BoneScytheItem(new Item.Properties().rarity(Rarity.UNCOMMON)
                    .attributes(SwordItem.createAttributes(BoneScytheItem.TIER, 4, -2.9F))));

    /** Itens na aba criativa de Rituais, na ordem. */
    public static List<Item> creativeItems() {
        return List.of(ALTAR_ITEM.get(), PHYLACTERY.get(), BONE_SCYTHE.get());
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
    }

    private NecroContent() {}
}
