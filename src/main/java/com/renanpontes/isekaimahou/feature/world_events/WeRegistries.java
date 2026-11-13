package com.renanpontes.isekaimahou.feature.world_events;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.world_events.entity.AlienGrunt;
import com.renanpontes.isekaimahou.feature.world_events.entity.AlienLarva;
import com.renanpontes.isekaimahou.feature.world_events.entity.AlienOverseer;
import com.renanpontes.isekaimahou.feature.world_events.entity.AlienSpitter;
import com.renanpontes.isekaimahou.feature.world_events.item.OmenLensItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros dos eventos do mundo: rocha/minério do meteoro, itens e os invasores alienígenas. */
public final class WeRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);

    // ---------------------------------------------------------------- blocos
    /** Rocha do meteoro (a casca da cratera): decoração escura. */
    public static final DeferredBlock<Block> METEORITE = BLOCKS.register("world_events_meteorite",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(3.0F, 9.0F)
                    .sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
    /** Minério Estelar: só existe dentro de meteoros. Pede picareta de ferro. */
    public static final DeferredBlock<DropExperienceBlock> STAR_ORE = BLOCKS.register("world_events_star_ore",
            () -> new DropExperienceBlock(UniformInt.of(3, 6), BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
                    .strength(4.5F, 9.0F).sound(SoundType.DEEPSLATE).lightLevel(s -> 6).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> STAR_METAL_BLOCK = BLOCKS.register("world_events_star_metal_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(5.0F, 9.0F)
                    .sound(SoundType.METAL).lightLevel(s -> 9).requiresCorrectToolForDrops()));
    /** Lâmpada Estelar: luz forte para decorar a cidade. */
    public static final DeferredBlock<Block> STAR_LAMP = BLOCKS.register("world_events_star_lamp",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.0F)
                    .sound(SoundType.GLASS).lightLevel(s -> 15)));

    public static final DeferredItem<BlockItem> METEORITE_ITEM = ITEMS.registerSimpleBlockItem(METEORITE);
    public static final DeferredItem<BlockItem> STAR_ORE_ITEM = ITEMS.registerSimpleBlockItem(STAR_ORE,
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> STAR_METAL_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(STAR_METAL_BLOCK,
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> STAR_LAMP_ITEM = ITEMS.registerSimpleBlockItem(STAR_LAMP);

    // ---------------------------------------------------------------- itens
    public static final DeferredItem<Item> STAR_METAL = ITEMS.registerSimpleItem("world_events_star_metal",
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ALIEN_CORE = ITEMS.registerSimpleItem("world_events_alien_core",
            new Item.Properties().rarity(Rarity.RARE).stacksTo(16));
    public static final DeferredItem<OmenLensItem> OMEN_LENS = ITEMS.register("world_events_omen_lens",
            () -> new OmenLensItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // ---------------------------------------------------------------- entidades
    public static final Supplier<EntityType<AlienGrunt>> ALIEN_GRUNT = ENTITIES.register("world_events_alien_grunt",
            () -> EntityType.Builder.of(AlienGrunt::new, MobCategory.MONSTER).sized(0.7F, 1.9F).eyeHeight(1.65F)
                    .clientTrackingRange(8).build("world_events_alien_grunt"));
    public static final Supplier<EntityType<AlienSpitter>> ALIEN_SPITTER = ENTITIES.register("world_events_alien_spitter",
            () -> EntityType.Builder.of(AlienSpitter::new, MobCategory.MONSTER).sized(0.8F, 1.5F).eyeHeight(1.25F)
                    .clientTrackingRange(8).build("world_events_alien_spitter"));
    public static final Supplier<EntityType<AlienLarva>> ALIEN_LARVA = ENTITIES.register("world_events_alien_larva",
            () -> EntityType.Builder.of(AlienLarva::new, MobCategory.MONSTER).sized(0.6F, 0.45F).eyeHeight(0.3F)
                    .clientTrackingRange(8).build("world_events_alien_larva"));
    public static final Supplier<EntityType<AlienOverseer>> ALIEN_OVERSEER = ENTITIES.register("world_events_alien_overseer",
            () -> EntityType.Builder.of(AlienOverseer::new, MobCategory.MONSTER).sized(1.5F, 3.0F).eyeHeight(2.4F)
                    .clientTrackingRange(10).build("world_events_alien_overseer"));

    public static final DeferredItem<DeferredSpawnEggItem> ALIEN_GRUNT_EGG = egg("world_events_alien_grunt", ALIEN_GRUNT, 0x3E2A5C, 0x7CFF6A);
    public static final DeferredItem<DeferredSpawnEggItem> ALIEN_SPITTER_EGG = egg("world_events_alien_spitter", ALIEN_SPITTER, 0x2E4A5C, 0x7CFF6A);
    public static final DeferredItem<DeferredSpawnEggItem> ALIEN_LARVA_EGG = egg("world_events_alien_larva", ALIEN_LARVA, 0x5C3E6A, 0xC8FF9A);
    public static final DeferredItem<DeferredSpawnEggItem> ALIEN_OVERSEER_EGG = egg("world_events_alien_overseer", ALIEN_OVERSEER, 0x1E1430, 0xB06CFF);

    private static DeferredItem<DeferredSpawnEggItem> egg(String id, Supplier<? extends EntityType<? extends Mob>> type, int back, int spots) {
        return ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(type, back, spots, new Item.Properties()));
    }

    public static List<DeferredItem<DeferredSpawnEggItem>> eggs() {
        return List.of(ALIEN_GRUNT_EGG, ALIEN_SPITTER_EGG, ALIEN_LARVA_EGG, ALIEN_OVERSEER_EGG);
    }

    /** Aba "Mundo". */
    public static List<DeferredItem<? extends Item>> worldItems() {
        return List.of(METEORITE_ITEM, STAR_ORE_ITEM, STAR_METAL, STAR_METAL_BLOCK_ITEM, STAR_LAMP_ITEM, ALIEN_CORE, OMEN_LENS);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(ALIEN_GRUNT.get(), AlienGrunt.createAttributes().build());
            e.put(ALIEN_SPITTER.get(), AlienSpitter.createAttributes().build());
            e.put(ALIEN_LARVA.get(), AlienLarva.createAttributes().build());
            e.put(ALIEN_OVERSEER.get(), AlienOverseer.createAttributes().build());
        });
    }

    private WeRegistries() {}
}
