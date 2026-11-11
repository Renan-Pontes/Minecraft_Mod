package com.renanpontes.isekaimahou.feature.dimensions;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.dimensions.block.CelestialBeaconBlock;
import com.renanpontes.isekaimahou.feature.dimensions.block.CelestialBeaconBlockEntity;
import com.renanpontes.isekaimahou.feature.dimensions.block.CelestialBeamBlock;
import com.renanpontes.isekaimahou.feature.dimensions.block.CloudBlock;
import com.renanpontes.isekaimahou.feature.dimensions.block.ConvergenceAltarBlock;
import com.renanpontes.isekaimahou.feature.dimensions.block.DimPortalBlock;
import com.renanpontes.isekaimahou.feature.dimensions.block.GlowCrystalBlock;
import com.renanpontes.isekaimahou.feature.dimensions.block.PortalFrameBlock;
import com.renanpontes.isekaimahou.feature.dimensions.block.SkyGrassBlock;
import com.renanpontes.isekaimahou.feature.dimensions.block.TwilightRiftBlock;
import com.renanpontes.isekaimahou.feature.dimensions.block.TwilightRiftBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do módulo de dimensões: blocos de terreno, portais, altar, block entities e o attachment de retorno. */
public final class DimRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);

    // ------------------------------------------------------------------ Plano Elemental
    public static final DeferredBlock<Block> FLOATING_STONE = BLOCKS.registerSimpleBlock("floating_stone", stone(MapColor.COLOR_LIGHT_BLUE, 1.5F)
            .sound(SoundType.DEEPSLATE));
    public static final DeferredBlock<Block> FLOATING_STONE_BRICKS = BLOCKS.registerSimpleBlock("floating_stone_bricks", stone(MapColor.COLOR_LIGHT_BLUE, 2.0F)
            .sound(SoundType.DEEPSLATE_BRICKS));
    public static final DeferredBlock<Block> EMBER_ROCK = BLOCKS.registerSimpleBlock("ember_rock", stone(MapColor.NETHER, 1.5F)
            .sound(SoundType.BASALT));
    public static final DeferredBlock<SkyGrassBlock> SKY_GRASS = BLOCKS.registerBlock("sky_grass", SkyGrassBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(0.6F).randomTicks().sound(SoundType.GRASS));
    public static final DeferredBlock<Block> SKY_SOIL = BLOCKS.registerSimpleBlock("sky_soil",
            BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_LIGHT_BLUE).strength(0.5F).sound(SoundType.ROOTED_DIRT));
    public static final DeferredBlock<CloudBlock> STORM_CLOUD = BLOCKS.register("storm_cloud", () -> new CloudBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.3F).sound(SoundType.WOOL), true));

    // ------------------------------------------------------------------ Abismo
    public static final DeferredBlock<Block> ABYSSAL_STONE = BLOCKS.registerSimpleBlock("abyssal_stone", stone(MapColor.COLOR_BLACK, 2.0F)
            .sound(SoundType.DEEPSLATE));
    public static final DeferredBlock<Block> ABYSSAL_STONE_BRICKS = BLOCKS.registerSimpleBlock("abyssal_stone_bricks", stone(MapColor.COLOR_BLACK, 2.5F)
            .sound(SoundType.DEEPSLATE_BRICKS));
    public static final DeferredBlock<GlowCrystalBlock> VOID_CRYSTAL = BLOCKS.register("void_crystal", () -> new GlowCrystalBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1.5F).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).lightLevel(s -> 6).emissiveRendering((s, l, p) -> true), false));
    public static final DeferredBlock<Block> ABYSSAL_MOSS = BLOCKS.registerSimpleBlock("abyssal_moss",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(0.3F).sound(SoundType.MOSS));

    // ------------------------------------------------------------------ Santuário Celestial
    public static final DeferredBlock<Block> CELESTIAL_MARBLE = BLOCKS.registerSimpleBlock("celestial_marble", stone(MapColor.QUARTZ, 1.5F)
            .sound(SoundType.CALCITE));
    public static final DeferredBlock<Block> CELESTIAL_MARBLE_BRICKS = BLOCKS.registerSimpleBlock("celestial_marble_bricks", stone(MapColor.QUARTZ, 2.0F)
            .sound(SoundType.CALCITE));
    public static final DeferredBlock<CloudBlock> CLOUD_BLOCK = BLOCKS.register("cloud_block", () -> new CloudBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.3F).sound(SoundType.WOOL)
                    .emissiveRendering((s, l, p) -> true), false));
    public static final DeferredBlock<GlowCrystalBlock> RADIANT_CRYSTAL = BLOCKS.register("radiant_crystal", () -> new GlowCrystalBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(1.5F).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).lightLevel(s -> 15).emissiveRendering((s, l, p) -> true), true));

    // ------------------------------------------------------------------ Fenda do Crepúsculo
    public static final DeferredBlock<Block> TWILIGHT_STONE = BLOCKS.registerSimpleBlock("twilight_stone", stone(MapColor.COLOR_PURPLE, 3.0F)
            .sound(SoundType.DEEPSLATE_TILES));

    // ------------------------------------------------------------------ Portais
    public static final DeferredBlock<PortalFrameBlock> ELEMENTAL_PORTAL_FRAME = BLOCKS.register("elemental_portal_frame", () -> new PortalFrameBlock(
            frame(MapColor.COLOR_LIGHT_BLUE).lightLevel(s -> 3), PortalKind.ELEMENTAL));
    public static final DeferredBlock<DimPortalBlock> ELEMENTAL_PORTAL = BLOCKS.register("elemental_portal", () -> new DimPortalBlock(
            portal(MapColor.COLOR_LIGHT_BLUE, 12), PortalKind.ELEMENTAL));
    public static final DeferredBlock<PortalFrameBlock> ABYSS_PORTAL_FRAME = BLOCKS.register("abyss_portal_frame", () -> new PortalFrameBlock(
            frame(MapColor.COLOR_BLACK).lightLevel(s -> 1), PortalKind.ABYSS));
    public static final DeferredBlock<DimPortalBlock> ABYSS_PORTAL = BLOCKS.register("abyss_portal", () -> new DimPortalBlock(
            portal(MapColor.COLOR_PURPLE, 9), PortalKind.ABYSS));
    public static final DeferredBlock<CelestialBeaconBlock> CELESTIAL_BEACON = BLOCKS.register("celestial_beacon", () -> new CelestialBeaconBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(3.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.AMETHYST).noOcclusion().pushReaction(PushReaction.BLOCK)
                    .lightLevel(s -> s.getValue(CelestialBeaconBlock.ACTIVE) ? 15 : 6)));
    public static final DeferredBlock<CelestialBeamBlock> CELESTIAL_PORTAL = BLOCKS.register("celestial_portal", () -> new CelestialBeamBlock(
            portal(MapColor.GOLD, 15)));
    public static final DeferredBlock<TwilightRiftBlock> TWILIGHT_RIFT = BLOCKS.register("twilight_rift", () -> new TwilightRiftBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(50.0F, 1200.0F).requiresCorrectToolForDrops()
                    .noCollission().noOcclusion().lightLevel(s -> 11).sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<ConvergenceAltarBlock> CONVERGENCE_ALTAR = BLOCKS.register("convergence_altar", () -> new ConvergenceAltarBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE_BRICKS).noOcclusion().lightLevel(s -> 9).pushReaction(PushReaction.BLOCK)));

    // ------------------------------------------------------------------ Itens dos blocos
    public static final DeferredItem<BlockItem> FLOATING_STONE_ITEM = ITEMS.registerSimpleBlockItem("floating_stone", FLOATING_STONE, new Item.Properties());
    public static final DeferredItem<BlockItem> FLOATING_STONE_BRICKS_ITEM = ITEMS.registerSimpleBlockItem("floating_stone_bricks", FLOATING_STONE_BRICKS, new Item.Properties());
    public static final DeferredItem<BlockItem> EMBER_ROCK_ITEM = ITEMS.registerSimpleBlockItem("ember_rock", EMBER_ROCK, new Item.Properties());
    public static final DeferredItem<BlockItem> SKY_GRASS_ITEM = ITEMS.registerSimpleBlockItem("sky_grass", SKY_GRASS, new Item.Properties());
    public static final DeferredItem<BlockItem> SKY_SOIL_ITEM = ITEMS.registerSimpleBlockItem("sky_soil", SKY_SOIL, new Item.Properties());
    public static final DeferredItem<BlockItem> STORM_CLOUD_ITEM = ITEMS.registerSimpleBlockItem("storm_cloud", STORM_CLOUD, new Item.Properties());
    public static final DeferredItem<BlockItem> ABYSSAL_STONE_ITEM = ITEMS.registerSimpleBlockItem("abyssal_stone", ABYSSAL_STONE, new Item.Properties());
    public static final DeferredItem<BlockItem> ABYSSAL_STONE_BRICKS_ITEM = ITEMS.registerSimpleBlockItem("abyssal_stone_bricks", ABYSSAL_STONE_BRICKS, new Item.Properties());
    public static final DeferredItem<BlockItem> VOID_CRYSTAL_ITEM = ITEMS.registerSimpleBlockItem("void_crystal", VOID_CRYSTAL, new Item.Properties());
    public static final DeferredItem<BlockItem> ABYSSAL_MOSS_ITEM = ITEMS.registerSimpleBlockItem("abyssal_moss", ABYSSAL_MOSS, new Item.Properties());
    public static final DeferredItem<BlockItem> CELESTIAL_MARBLE_ITEM = ITEMS.registerSimpleBlockItem("celestial_marble", CELESTIAL_MARBLE, new Item.Properties());
    public static final DeferredItem<BlockItem> CELESTIAL_MARBLE_BRICKS_ITEM = ITEMS.registerSimpleBlockItem("celestial_marble_bricks", CELESTIAL_MARBLE_BRICKS, new Item.Properties());
    public static final DeferredItem<BlockItem> CLOUD_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("cloud_block", CLOUD_BLOCK, new Item.Properties());
    public static final DeferredItem<BlockItem> RADIANT_CRYSTAL_ITEM = ITEMS.registerSimpleBlockItem("radiant_crystal", RADIANT_CRYSTAL, new Item.Properties());
    public static final DeferredItem<BlockItem> TWILIGHT_STONE_ITEM = ITEMS.registerSimpleBlockItem("twilight_stone", TWILIGHT_STONE, new Item.Properties());
    // Blocos com regra de ativação: o item explica como usar (DimBlockItem).
    public static final DeferredItem<BlockItem> ELEMENTAL_PORTAL_FRAME_ITEM = ITEMS.register("elemental_portal_frame",
            () -> new DimBlockItem(ELEMENTAL_PORTAL_FRAME.get(), new Item.Properties().rarity(Rarity.UNCOMMON),
                    () -> DimBlockItem.frame(PortalKind.ELEMENTAL)));
    public static final DeferredItem<BlockItem> ABYSS_PORTAL_FRAME_ITEM = ITEMS.register("abyss_portal_frame",
            () -> new DimBlockItem(ABYSS_PORTAL_FRAME.get(), new Item.Properties().rarity(Rarity.RARE),
                    () -> DimBlockItem.frame(PortalKind.ABYSS)));
    public static final DeferredItem<BlockItem> CELESTIAL_BEACON_ITEM = ITEMS.register("celestial_beacon",
            () -> new DimBlockItem(CELESTIAL_BEACON.get(), new Item.Properties().rarity(Rarity.RARE), DimBlockItem::beacon));
    public static final DeferredItem<BlockItem> TWILIGHT_RIFT_ITEM = ITEMS.register("twilight_rift",
            () -> new DimBlockItem(TWILIGHT_RIFT.get(), new Item.Properties().rarity(Rarity.EPIC).fireResistant(), DimBlockItem::rift));
    public static final DeferredItem<BlockItem> CONVERGENCE_ALTAR_ITEM = ITEMS.register("convergence_altar",
            () -> new DimBlockItem(CONVERGENCE_ALTAR.get(), new Item.Properties().rarity(Rarity.EPIC), DimBlockItem::altar));

    /** Ordem dos itens na aba criativa "Mundo". */
    public static final List<Supplier<? extends Item>> TAB_ORDER = List.of(
            CONVERGENCE_ALTAR_ITEM, ELEMENTAL_PORTAL_FRAME_ITEM, ABYSS_PORTAL_FRAME_ITEM, CELESTIAL_BEACON_ITEM, TWILIGHT_RIFT_ITEM,
            FLOATING_STONE_ITEM, FLOATING_STONE_BRICKS_ITEM, EMBER_ROCK_ITEM, SKY_GRASS_ITEM, SKY_SOIL_ITEM, STORM_CLOUD_ITEM,
            ABYSSAL_STONE_ITEM, ABYSSAL_STONE_BRICKS_ITEM, ABYSSAL_MOSS_ITEM, VOID_CRYSTAL_ITEM,
            CELESTIAL_MARBLE_ITEM, CELESTIAL_MARBLE_BRICKS_ITEM, CLOUD_BLOCK_ITEM, RADIANT_CRYSTAL_ITEM, TWILIGHT_STONE_ITEM);

    // ------------------------------------------------------------------ Block entities
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<CelestialBeaconBlockEntity>> CELESTIAL_BEACON_BE = BLOCK_ENTITIES.register("celestial_beacon",
            () -> BlockEntityType.Builder.of(CelestialBeaconBlockEntity::new, CELESTIAL_BEACON.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<TwilightRiftBlockEntity>> TWILIGHT_RIFT_BE = BLOCK_ENTITIES.register("twilight_rift",
            () -> BlockEntityType.Builder.of(TwilightRiftBlockEntity::new, TWILIGHT_RIFT.get()).build(null));

    // ------------------------------------------------------------------ Attachment
    /** Pontos de retorno por dimensão do mod: "<dim>" -> {dim, x, y, z, yaw}. Sobrevive à morte. */
    public static final Supplier<AttachmentType<CompoundTag>> RETURN_POINTS = ATTACHMENTS.register("dimensions_return_points",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    private static BlockBehaviour.Properties stone(MapColor color, float hardness) {
        return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM)
                .strength(hardness, 6.0F).requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties frame(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM)
                .strength(5.0F, 1200.0F).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS).pushReaction(PushReaction.BLOCK);
    }

    private static BlockBehaviour.Properties portal(MapColor color, int light) {
        return BlockBehaviour.Properties.of().mapColor(color).noCollission().strength(-1.0F, 3600000.0F).noLootTable()
                .sound(SoundType.GLASS).lightLevel(s -> light).pushReaction(PushReaction.BLOCK)
                .emissiveRendering((s, l, p) -> true);
    }

    private DimRegistry() {}
}
