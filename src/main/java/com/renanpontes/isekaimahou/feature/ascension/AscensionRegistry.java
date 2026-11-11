package com.renanpontes.isekaimahou.feature.ascension;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ascension.block.AscensionCoreBlock;
import com.renanpontes.isekaimahou.feature.ascension.block.AscensionCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.ascension.block.AscensionPedestalBlock;
import com.renanpontes.isekaimahou.feature.ascension.block.AscensionPedestalBlockEntity;
import com.renanpontes.isekaimahou.feature.ascension.block.ManaCrystalBlock;
import com.renanpontes.isekaimahou.feature.ascension.block.RuneStoneBlock;
import com.renanpontes.isekaimahou.feature.ascension.entity.AscensionShadowEntity;
import com.renanpontes.isekaimahou.feature.ascension.entity.TribulationBoltEntity;
import com.renanpontes.isekaimahou.feature.ascension.item.AscensionTributeItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo Ascensão: blocos do ritual, block entities e a Sombra da Tribulação. */
public final class AscensionRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);

    // ---------------- Blocos ----------------
    public static final DeferredBlock<AscensionCoreBlock> CORE = BLOCKS.registerBlock("ascension_core", AscensionCoreBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE_BRICKS).lightLevel(s -> 7).noOcclusion().pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<AscensionPedestalBlock> PEDESTAL = BLOCKS.registerBlock("ascension_pedestal", AscensionPedestalBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.POLISHED_DEEPSLATE).noOcclusion().pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<ManaCrystalBlock> MANA_CRYSTAL_BLOCK = BLOCKS.registerBlock("mana_crystal_block", ManaCrystalBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1.5F).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).lightLevel(s -> 10));
    public static final DeferredBlock<RuneStoneBlock> RUNE_STONE = BLOCKS.registerBlock("rune_stone", RuneStoneBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE_BRICKS).lightLevel(s -> 4)
                    // Pistão não arranca a Pedra Rúnica de um altar público (as outras peças já são BLOCK).
                    .pushReaction(PushReaction.BLOCK));

    // ---------------- Itens dos blocos ----------------
    public static final DeferredItem<BlockItem> CORE_ITEM = ITEMS.registerSimpleBlockItem("ascension_core", CORE,
            new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> PEDESTAL_ITEM = ITEMS.registerSimpleBlockItem("ascension_pedestal", PEDESTAL,
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> MANA_CRYSTAL_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("mana_crystal_block", MANA_CRYSTAL_BLOCK,
            new Item.Properties());
    public static final DeferredItem<BlockItem> RUNE_STONE_ITEM = ITEMS.registerSimpleBlockItem("rune_stone", RUNE_STONE,
            new Item.Properties());

    // ---------------- Block entities ----------------
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<AscensionCoreBlockEntity>> CORE_BE = BLOCK_ENTITIES.register("ascension_core",
            () -> BlockEntityType.Builder.of(AscensionCoreBlockEntity::new, CORE.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<AscensionPedestalBlockEntity>> PEDESTAL_BE = BLOCK_ENTITIES.register("ascension_pedestal",
            () -> BlockEntityType.Builder.of(AscensionPedestalBlockEntity::new, PEDESTAL.get()).build(null));

    // ---------------- Entidade: a Sombra ----------------
    public static final Supplier<EntityType<AscensionShadowEntity>> SHADOW = ENTITIES.register("ascension_shadow",
            () -> EntityType.Builder.<AscensionShadowEntity>of(AscensionShadowEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).build("ascension_shadow"));
    /** Raio arcano da Tribulação (só visual). */
    public static final Supplier<EntityType<TribulationBoltEntity>> BOLT = ENTITIES.register("tribulation_bolt",
            () -> EntityType.Builder.<TribulationBoltEntity>of(TribulationBoltEntity::new, MobCategory.MISC)
                    .noSave().sized(0.0F, 0.0F).clientTrackingRange(8).updateInterval(Integer.MAX_VALUE).build("tribulation_bolt"));
    public static final DeferredItem<DeferredSpawnEggItem> SHADOW_EGG = ITEMS.register("ascension_shadow_spawn_egg",
            () -> new DeferredSpawnEggItem(SHADOW, 0x140C1E, 0x9B6BFF, new Item.Properties()));

    // ---------------- Tributos prontos: "Tributo do Círculo N" (2..10) ----------------
    /** Círculo-alvo → item que vale como o tributo completo daquele Círculo (entregue pelas missões "A Ascensão"). */
    public static final Map<Integer, DeferredItem<AscensionTributeItem>> TRIBUTES;

    static {
        Map<Integer, DeferredItem<AscensionTributeItem>> map = new LinkedHashMap<>();
        for (int n = 2; n <= 10; n++) {
            final int circle = n;
            map.put(n, ITEMS.register("ascension_tribute_" + n, () -> new AscensionTributeItem(circle, new Item.Properties())));
        }
        TRIBUTES = Collections.unmodifiableMap(map);
    }

    /** Item "Tributo do Círculo N", ou null fora de 2..10. */
    @Nullable
    public static Item tributeItem(int targetCircle) {
        DeferredItem<AscensionTributeItem> d = TRIBUTES.get(targetCircle);
        return d == null ? null : d.get();
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
    }

    private AscensionRegistry() {}
}
