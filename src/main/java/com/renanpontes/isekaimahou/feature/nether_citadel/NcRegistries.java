package com.renanpontes.isekaimahou.feature.nether_citadel;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.nether_citadel.block.InfernalForgeBlock;
import com.renanpontes.isekaimahou.feature.nether_citadel.block.PactAltarBlock;
import com.renanpontes.isekaimahou.feature.nether_citadel.effect.DemonDebtEffect;
import com.renanpontes.isekaimahou.feature.nether_citadel.effect.DemonPactEffect;
import com.renanpontes.isekaimahou.feature.nether_citadel.entity.DemonKnight;
import com.renanpontes.isekaimahou.feature.nether_citadel.entity.DemonLord;
import com.renanpontes.isekaimahou.feature.nether_citadel.entity.DemonSuccubus;
import com.renanpontes.isekaimahou.feature.nether_citadel.item.AntimagicBladeItem;
import com.renanpontes.isekaimahou.feature.nether_citadel.item.NcLoreItem;
import com.renanpontes.isekaimahou.feature.nether_citadel.item.SunKatanaItem;

import net.minecraft.Util;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros da Cidadela Demoníaca: Adamantita (minério/lingote/equipamento), Altar do Pacto, Forja Infernal, demônios e tesouros. */
public final class NcRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, IsekaiMahou.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IsekaiMahou.MODID);

    // ---------------------------------------------------------------- componente
    /** Infusão da Forja Infernal gravada na arma: "fire" ou "shadow". */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> INFUSION = COMPONENTS.registerComponentType(
            "citadel_infusion", b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    // ---------------------------------------------------------------- efeitos
    public static final DeferredHolder<MobEffect, DemonPactEffect> DEMON_PACT = EFFECTS.register("demon_pact", DemonPactEffect::new);
    public static final DeferredHolder<MobEffect, DemonDebtEffect> DEMON_DEBT = EFFECTS.register("demon_debt", DemonDebtEffect::new);

    // ---------------------------------------------------------------- Adamantita
    /** Adamantita ≈ netherite+: corta mais rápido, dura mais e bate um pouco mais forte. */
    public static final Tier ADAMANTITE_TIER = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2600, 10.0F, 4.5F, 18,
            () -> Ingredient.of(NcRegistries.ADAMANTITE_INGOT.get()));

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ADAMANTITE_MATERIAL = MATERIALS.register("adamantite",
            () -> new ArmorMaterial(Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.BOOTS, 3);
                m.put(ArmorItem.Type.LEGGINGS, 6);
                m.put(ArmorItem.Type.CHESTPLATE, 8);
                m.put(ArmorItem.Type.HELMET, 3);
                m.put(ArmorItem.Type.BODY, 11);
            }), 18, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(NcRegistries.ADAMANTITE_INGOT.get()),
                    List.of(new ArmorMaterial.Layer(IsekaiMahou.id("adamantite"))), 3.5F, 0.12F));

    public static final DeferredBlock<DropExperienceBlock> ADAMANTITE_ORE = BLOCKS.register("adamantite_ore",
            () -> new DropExperienceBlock(UniformInt.of(3, 7), BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .requiresCorrectToolForDrops().strength(32.0F, 1200.0F).sound(SoundType.ANCIENT_DEBRIS)));
    public static final DeferredBlock<Block> ADAMANTITE_BLOCK = BLOCKS.register("adamantite_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).requiresCorrectToolForDrops()
                    .strength(50.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK)));
    public static final DeferredBlock<PactAltarBlock> PACT_ALTAR = BLOCKS.register("demon_pact_altar",
            () -> new PactAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops()
                    .strength(6.0F, 1200.0F).sound(SoundType.POLISHED_DEEPSLATE).lightLevel(s -> 7).noOcclusion()));
    public static final DeferredBlock<InfernalForgeBlock> INFERNAL_FORGE = BLOCKS.register("demon_infernal_forge",
            () -> new InfernalForgeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NETHER).requiresCorrectToolForDrops()
                    .strength(5.0F, 1200.0F).sound(SoundType.NETHER_BRICKS).lightLevel(s -> 13).noOcclusion()));

    public static final DeferredItem<BlockItem> ADAMANTITE_ORE_ITEM = ITEMS.registerSimpleBlockItem(ADAMANTITE_ORE,
            new Item.Properties().fireResistant());
    public static final DeferredItem<BlockItem> ADAMANTITE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(ADAMANTITE_BLOCK,
            new Item.Properties().fireResistant());
    public static final DeferredItem<BlockItem> PACT_ALTAR_ITEM = ITEMS.registerSimpleBlockItem(PACT_ALTAR,
            new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> INFERNAL_FORGE_ITEM = ITEMS.registerSimpleBlockItem(INFERNAL_FORGE,
            new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<Item> RAW_ADAMANTITE = ITEMS.registerSimpleItem("raw_adamantite", new Item.Properties().fireResistant());
    public static final DeferredItem<Item> ADAMANTITE_INGOT = ITEMS.registerSimpleItem("adamantite_ingot",
            new Item.Properties().fireResistant().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<SwordItem> ADAMANTITE_SWORD = ITEMS.register("adamantite_sword",
            () -> new SwordItem(ADAMANTITE_TIER, new Item.Properties().fireResistant()
                    .attributes(SwordItem.createAttributes(ADAMANTITE_TIER, 3, -2.4F))));
    public static final DeferredItem<PickaxeItem> ADAMANTITE_PICKAXE = ITEMS.register("adamantite_pickaxe",
            () -> new PickaxeItem(ADAMANTITE_TIER, new Item.Properties().fireResistant()
                    .attributes(PickaxeItem.createAttributes(ADAMANTITE_TIER, 1.0F, -2.8F))));
    public static final DeferredItem<AxeItem> ADAMANTITE_AXE = ITEMS.register("adamantite_axe",
            () -> new AxeItem(ADAMANTITE_TIER, new Item.Properties().fireResistant()
                    .attributes(AxeItem.createAttributes(ADAMANTITE_TIER, 5.0F, -3.0F))));

    public static final Map<ArmorItem.Type, DeferredItem<ArmorItem>> ADAMANTITE_ARMOR = new EnumMap<>(ArmorItem.Type.class);

    static {
        armor(ArmorItem.Type.HELMET, "adamantite_helmet");
        armor(ArmorItem.Type.CHESTPLATE, "adamantite_chestplate");
        armor(ArmorItem.Type.LEGGINGS, "adamantite_leggings");
        armor(ArmorItem.Type.BOOTS, "adamantite_boots");
    }

    private static void armor(ArmorItem.Type type, String id) {
        ADAMANTITE_ARMOR.put(type, ITEMS.register(id, () -> new ArmorItem(ADAMANTITE_MATERIAL, type,
                new Item.Properties().fireResistant().durability(type.getDurability(40)))));
    }

    // ---------------------------------------------------------------- tesouros de anime e drops
    public static final DeferredItem<AntimagicBladeItem> ANTIMAGIC_BLADE = ITEMS.register("demon_antimagic_blade",
            () -> new AntimagicBladeItem(ADAMANTITE_TIER, new Item.Properties().fireResistant().rarity(Rarity.EPIC)
                    .attributes(SwordItem.createAttributes(ADAMANTITE_TIER, 6, -3.0F))));
    public static final DeferredItem<SunKatanaItem> SUN_KATANA = ITEMS.register("citadel_sun_katana",
            () -> new SunKatanaItem(ADAMANTITE_TIER, new Item.Properties().fireResistant().rarity(Rarity.EPIC)
                    .attributes(SwordItem.createAttributes(ADAMANTITE_TIER, 2, -2.0F))));
    public static final DeferredItem<NcLoreItem> DEMON_LORD_SIGIL = ITEMS.register("demon_lord_sigil",
            () -> new NcLoreItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant(), true));

    // ---------------------------------------------------------------- entidades
    public static final Supplier<EntityType<DemonLord>> DEMON_LORD = ENTITIES.register("demon_lord",
            () -> EntityType.Builder.of(DemonLord::new, MobCategory.MONSTER).sized(1.3F, 3.4F).eyeHeight(3.0F)
                    .fireImmune().clientTrackingRange(10).build("demon_lord"));
    public static final Supplier<EntityType<DemonKnight>> DEMON_KNIGHT = ENTITIES.register("demon_knight",
            () -> EntityType.Builder.of(DemonKnight::new, MobCategory.MONSTER).sized(0.8F, 2.3F).eyeHeight(2.05F)
                    .fireImmune().clientTrackingRange(8).build("demon_knight"));
    public static final Supplier<EntityType<DemonSuccubus>> DEMON_SUCCUBUS = ENTITIES.register("demon_succubus",
            () -> EntityType.Builder.of(DemonSuccubus::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.7F)
                    .fireImmune().clientTrackingRange(8).build("demon_succubus"));

    public static final DeferredItem<DeferredSpawnEggItem> DEMON_LORD_EGG = egg("demon_lord", DEMON_LORD, 0x2A0A0E, 0xD4242C);
    public static final DeferredItem<DeferredSpawnEggItem> DEMON_KNIGHT_EGG = egg("demon_knight", DEMON_KNIGHT, 0x2B2730, 0xE0582A);
    public static final DeferredItem<DeferredSpawnEggItem> DEMON_SUCCUBUS_EGG = egg("demon_succubus", DEMON_SUCCUBUS, 0x5A1838, 0xC86AE8);

    private static DeferredItem<DeferredSpawnEggItem> egg(String id, Supplier<? extends EntityType<? extends Mob>> type, int back, int spots) {
        return ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(type, back, spots, new Item.Properties()));
    }

    public static List<DeferredItem<DeferredSpawnEggItem>> eggs() {
        return List.of(DEMON_LORD_EGG, DEMON_KNIGHT_EGG, DEMON_SUCCUBUS_EGG);
    }

    /** Itens da aba "Mundo" (materiais e blocos). */
    public static List<DeferredItem<? extends Item>> worldItems() {
        return List.of(ADAMANTITE_ORE_ITEM, RAW_ADAMANTITE, ADAMANTITE_INGOT, ADAMANTITE_BLOCK_ITEM, PACT_ALTAR_ITEM, INFERNAL_FORGE_ITEM);
    }

    /** Itens da aba "Magia" (equipamento, tesouros e drops do chefe). */
    public static List<DeferredItem<? extends Item>> gearItems() {
        return List.of(ADAMANTITE_SWORD, ADAMANTITE_PICKAXE, ADAMANTITE_AXE,
                ADAMANTITE_ARMOR.get(ArmorItem.Type.HELMET), ADAMANTITE_ARMOR.get(ArmorItem.Type.CHESTPLATE),
                ADAMANTITE_ARMOR.get(ArmorItem.Type.LEGGINGS), ADAMANTITE_ARMOR.get(ArmorItem.Type.BOOTS),
                ANTIMAGIC_BLADE, SUN_KATANA, DEMON_LORD_SIGIL);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        MATERIALS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(DEMON_LORD.get(), DemonLord.createAttributes().build());
            e.put(DEMON_KNIGHT.get(), DemonKnight.createAttributes().build());
            e.put(DEMON_SUCCUBUS.get(), DemonSuccubus.createAttributes().build());
        });
        // Nascem só pelas "spawn_overrides" da Cidadela (o teto de mobs do Nether limita); a luz não importa lá dentro.
        modBus.addListener((RegisterSpawnPlacementsEvent e) -> {
            e.register(DEMON_KNIGHT.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Monster::checkAnyLightMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
            e.register(DEMON_SUCCUBUS.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Monster::checkAnyLightMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        });
    }

    private NcRegistries() {}
}
