package com.renanpontes.isekaimahou.feature.end_library;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.end_library.entity.StarDevourer;
import com.renanpontes.isekaimahou.feature.end_library.entity.StarWisp;
import com.renanpontes.isekaimahou.feature.end_library.entity.TreasureBlade;
import com.renanpontes.isekaimahou.feature.end_library.entity.VoidScribe;
import com.renanpontes.isekaimahou.feature.end_library.item.OverlordStaffItem;
import com.renanpontes.isekaimahou.feature.end_library.item.StarGear;
import com.renanpontes.isekaimahou.feature.end_library.item.TreasureGateItem;
import com.renanpontes.isekaimahou.feature.end_library.item.VoidTomeItem;
import com.renanpontes.isekaimahou.feature.end_library.world.EndLibraryStructure;

import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo: estrutura, minério, estante, equipamento estelar, tesouros, criaturas, chefe e projétil. */
public final class ElRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, IsekaiMahou.MODID);
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, IsekaiMahou.MODID);

    // ---------------- Estrutura ----------------
    public static final DeferredHolder<StructureType<?>, StructureType<EndLibraryStructure>> END_LIBRARY_STRUCTURE =
            STRUCTURE_TYPES.register("end_library", () -> () -> EndLibraryStructure.CODEC);

    // ---------------- Blocos ----------------
    /** Minério de Fragmento Estelar: raro na pedra do End das ilhas externas; brilha de leve. */
    public static final DeferredBlock<DropExperienceBlock> STAR_FRAGMENT_ORE = BLOCKS.registerBlock("star_fragment_ore",
            p -> new DropExperienceBlock(UniformInt.of(4, 8), p),
            BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE).strength(4.5F, 9.0F).requiresCorrectToolForDrops()
                    .mapColor(MapColor.SAND).lightLevel(s -> 4));
    /** Estante do Vazio: decoração da biblioteca; conta como estante para a mesa de encantamento. */
    public static final DeferredBlock<Block> VOID_BOOKSHELF = BLOCKS.registerBlock("void_bookshelf", Block::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BOOKSHELF).mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> 5));

    public static final DeferredItem<BlockItem> STAR_FRAGMENT_ORE_ITEM = ITEMS.registerSimpleBlockItem("star_fragment_ore",
            STAR_FRAGMENT_ORE, new Item.Properties());
    public static final DeferredItem<BlockItem> VOID_BOOKSHELF_ITEM = ITEMS.registerSimpleBlockItem("void_bookshelf",
            VOID_BOOKSHELF, new Item.Properties());

    // ---------------- Materiais ----------------
    public static final DeferredItem<Item> STAR_FRAGMENT = ITEMS.register("star_fragment",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant()));
    public static final DeferredItem<Item> STAR_INGOT = ITEMS.register("star_ingot",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant()));
    public static final DeferredItem<SmithingTemplateItem> STAR_UPGRADE_TEMPLATE = ITEMS.register("star_upgrade_smithing_template",
            ElRegistries::starTemplate);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> STAR_MATERIAL = MATERIALS.register("star",
            () -> new ArmorMaterial(Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.BOOTS, 4);
                m.put(ArmorItem.Type.LEGGINGS, 7);
                m.put(ArmorItem.Type.CHESTPLATE, 9);
                m.put(ArmorItem.Type.HELMET, 4);
                m.put(ArmorItem.Type.BODY, 12);
            }), 18, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(ElRegistries.STAR_INGOT.get()),
                    List.of(new ArmorMaterial.Layer(IsekaiMahou.id("star"))), 4.0F, 0.15F));

    // ---------------- Equipamento estelar (exige Círculo 7) ----------------
    public static final DeferredItem<StarGear.Sword> STAR_SWORD = ITEMS.register("star_sword", () -> new StarGear.Sword());
    public static final DeferredItem<StarGear.Pickaxe> STAR_PICKAXE = ITEMS.register("star_pickaxe", () -> new StarGear.Pickaxe());
    public static final DeferredItem<StarGear.Armor> STAR_HELMET = armor("star_helmet", ArmorItem.Type.HELMET);
    public static final DeferredItem<StarGear.Armor> STAR_CHESTPLATE = armor("star_chestplate", ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<StarGear.Armor> STAR_LEGGINGS = armor("star_leggings", ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<StarGear.Armor> STAR_BOOTS = armor("star_boots", ArmorItem.Type.BOOTS);

    // ---------------- Tesouros ----------------
    public static final DeferredItem<VoidTomeItem> VOID_TOME = ITEMS.register("void_tome",
            () -> new VoidTomeItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final DeferredItem<TreasureGateItem> VOID_TREASURE_GATE = ITEMS.register("void_treasure_gate",
            () -> new TreasureGateItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<OverlordStaffItem> VOID_OVERLORD_STAFF = ITEMS.register("void_overlord_staff",
            () -> new OverlordStaffItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    // ---------------- Entidades ----------------
    public static final Supplier<EntityType<StarDevourer>> STAR_DEVOURER = ENTITIES.register("star_devourer",
            () -> EntityType.Builder.of(StarDevourer::new, MobCategory.MONSTER)
                    .sized(3.0F, 3.2F).eyeHeight(2.6F).fireImmune().clientTrackingRange(10).build("star_devourer"));
    public static final Supplier<EntityType<VoidScribe>> VOID_SCRIBE = ENTITIES.register("void_scribe",
            () -> EntityType.Builder.of(VoidScribe::new, MobCategory.MONSTER)
                    .sized(0.7F, 2.2F).eyeHeight(1.9F).clientTrackingRange(8).build("void_scribe"));
    public static final Supplier<EntityType<StarWisp>> STAR_WISP = ENTITIES.register("star_wisp",
            () -> EntityType.Builder.of(StarWisp::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.6F).eyeHeight(0.3F).fireImmune().clientTrackingRange(8).build("star_wisp"));
    public static final Supplier<EntityType<TreasureBlade>> TREASURE_BLADE = ENTITIES.register("void_treasure_blade",
            () -> EntityType.Builder.<TreasureBlade>of(TreasureBlade::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(10).build("void_treasure_blade"));

    public static final DeferredItem<DeferredSpawnEggItem> STAR_DEVOURER_EGG = egg("star_devourer", STAR_DEVOURER, 0x1A0F2E, 0xFFE9A8);
    public static final DeferredItem<DeferredSpawnEggItem> VOID_SCRIBE_EGG = egg("void_scribe", VOID_SCRIBE, 0x2B1F4A, 0x7FE8FF);
    public static final DeferredItem<DeferredSpawnEggItem> STAR_WISP_EGG = egg("star_wisp", STAR_WISP, 0xFFF2B0, 0x8A5CFF);

    private static DeferredItem<StarGear.Armor> armor(String id, ArmorItem.Type type) {
        return ITEMS.register(id, () -> new StarGear.Armor(STAR_MATERIAL, type));
    }

    private static DeferredItem<DeferredSpawnEggItem> egg(String id, Supplier<? extends EntityType<? extends Mob>> type, int back, int spots) {
        return ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(type, back, spots, new Item.Properties()));
    }

    private static SmithingTemplateItem starTemplate() {
        String k = "item.isekai_mahou.star_upgrade_smithing_template.";
        return new SmithingTemplateItem(
                Component.translatable(k + "applies_to").withStyle(net.minecraft.ChatFormatting.BLUE),
                Component.translatable(k + "ingredients").withStyle(net.minecraft.ChatFormatting.BLUE),
                Component.translatable(k + "upgrade").withStyle(net.minecraft.ChatFormatting.GRAY),
                Component.translatable(k + "base_slot"),
                Component.translatable(k + "additions_slot"),
                List.of(mcIcon("item/empty_armor_slot_helmet"), mcIcon("item/empty_slot_sword"), mcIcon("item/empty_armor_slot_chestplate"),
                        mcIcon("item/empty_slot_pickaxe"), mcIcon("item/empty_armor_slot_leggings"), mcIcon("item/empty_armor_slot_boots")),
                List.of(mcIcon("item/empty_slot_ingot")));
    }

    private static ResourceLocation mcIcon(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    /** Itens da aba Mundo (materiais, blocos, equipamento). */
    public static List<DeferredItem<? extends Item>> worldItems() {
        return List.of(STAR_FRAGMENT_ORE_ITEM, VOID_BOOKSHELF_ITEM, STAR_FRAGMENT, STAR_INGOT, STAR_UPGRADE_TEMPLATE,
                STAR_SWORD, STAR_PICKAXE, STAR_HELMET, STAR_CHESTPLATE, STAR_LEGGINGS, STAR_BOOTS);
    }

    /** Itens da aba Magia (tesouros). */
    public static List<DeferredItem<? extends Item>> magicItems() {
        return List.of(VOID_TOME, VOID_TREASURE_GATE, VOID_OVERLORD_STAFF);
    }

    public static List<DeferredItem<DeferredSpawnEggItem>> eggs() {
        return List.of(STAR_DEVOURER_EGG, VOID_SCRIBE_EGG, STAR_WISP_EGG);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        MATERIALS.register(modBus);
        STRUCTURE_TYPES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(STAR_DEVOURER.get(), StarDevourer.createAttributes().build());
            e.put(VOID_SCRIBE.get(), VoidScribe.createAttributes().build());
            e.put(STAR_WISP.get(), StarWisp.createAttributes().build());
        });
        modBus.addListener(ElRegistries::onSpawnPlacements);
    }

    /** Criaturas do vazio: no escuro das ilhas do End; geradores da biblioteca funcionam mesmo com as luzes. */
    private static void onSpawnPlacements(RegisterSpawnPlacementsEvent e) {
        e.register(VOID_SCRIBE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> reason == MobSpawnType.SPAWNER
                        || Monster.checkMonsterSpawnRules(type, level, reason, pos, random),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        e.register(STAR_WISP.get(), SpawnPlacementTypes.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> reason == MobSpawnType.SPAWNER
                        || Monster.checkMonsterSpawnRules(type, level, reason, pos, random),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private ElRegistries() {}
}
