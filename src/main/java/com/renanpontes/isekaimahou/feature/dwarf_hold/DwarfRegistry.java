package com.renanpontes.isekaimahou.feature.dwarf_hold;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.dwarf_hold.block.RuneForgeBlock;
import com.renanpontes.isekaimahou.feature.dwarf_hold.entity.DwarfEntity;
import com.renanpontes.isekaimahou.feature.dwarf_hold.item.AppraisalMonocleItem;
import com.renanpontes.isekaimahou.feature.dwarf_hold.item.HeroShieldItem;
import com.renanpontes.isekaimahou.feature.dwarf_hold.item.MithrilArmorItem;

import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registros do dwarf_hold: Mithril (minério, lingote, ferramentas, armadura, Machado de Guerra), Forja Rúnica,
 * os tesouros (Escudo do Herói, Monóculo de Avaliação), os anões (comerciante e ferreiro) e o componente da runa.
 */
public final class DwarfRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, IsekaiMahou.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IsekaiMahou.MODID);

    // ---------------- Componente: runa gravada (id da runa) ----------------
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> RUNE = COMPONENTS.registerComponentType("dwarf_rune",
            b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));

    // ---------------- Blocos ----------------
    /** Só no deepslate abaixo de y 0 (raro). Dá Mithril Bruto + XP como o diamante. */
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_MITHRIL_ORE = BLOCKS.registerBlock("deepslate_mithril_ore",
            p -> new DropExperienceBlock(UniformInt.of(3, 7), p),
            BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_DIAMOND_ORE).mapColor(MapColor.DEEPSLATE).lightLevel(s -> 2));
    public static final DeferredBlock<Block> MITHRIL_BLOCK = BLOCKS.registerSimpleBlock("mithril_block",
            BlockBehaviour.Properties.ofFullCopy(Blocks.DIAMOND_BLOCK).mapColor(MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<Block> RAW_MITHRIL_BLOCK = BLOCKS.registerSimpleBlock("raw_mithril_block",
            BlockBehaviour.Properties.ofFullCopy(Blocks.RAW_IRON_BLOCK).mapColor(MapColor.COLOR_LIGHT_GRAY));
    public static final DeferredBlock<RuneForgeBlock> RUNE_FORGE = BLOCKS.registerBlock("dwarf_rune_forge", RuneForgeBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.ANVIL).mapColor(MapColor.DEEPSLATE).lightLevel(s -> 9).noOcclusion());

    public static final DeferredItem<BlockItem> DEEPSLATE_MITHRIL_ORE_ITEM = ITEMS.registerSimpleBlockItem("deepslate_mithril_ore",
            DEEPSLATE_MITHRIL_ORE, new Item.Properties());
    public static final DeferredItem<BlockItem> MITHRIL_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("mithril_block", MITHRIL_BLOCK,
            new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> RAW_MITHRIL_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("raw_mithril_block", RAW_MITHRIL_BLOCK,
            new Item.Properties());
    public static final DeferredItem<BlockItem> RUNE_FORGE_ITEM = ITEMS.registerSimpleBlockItem("dwarf_rune_forge", RUNE_FORGE,
            new Item.Properties().rarity(Rarity.UNCOMMON));

    // ---------------- Materiais ----------------
    public static final DeferredItem<Item> RAW_MITHRIL = ITEMS.registerSimpleItem("raw_mithril", new Item.Properties());
    public static final DeferredItem<Item> MITHRIL_INGOT = ITEMS.registerSimpleItem("mithril_ingot", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> MITHRIL_NUGGET = ITEMS.registerSimpleItem("mithril_nugget", new Item.Properties());

    /** Entre o diamante (1561, 8, +3, 10) e a netherita (2031, 9, +4, 15); encantabilidade alta: forjado por anões. */
    public static final Tier MITHRIL_TIER = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1800, 8.5F, 3.5F, 18,
            () -> Ingredient.of(MITHRIL_INGOT.get()));

    // ---------------- Ferramentas e armas ----------------
    public static final DeferredItem<SwordItem> MITHRIL_SWORD = ITEMS.register("mithril_sword", () -> new SwordItem(MITHRIL_TIER,
            new Item.Properties().attributes(SwordItem.createAttributes(MITHRIL_TIER, 3, -2.4F))));
    public static final DeferredItem<PickaxeItem> MITHRIL_PICKAXE = ITEMS.register("mithril_pickaxe", () -> new PickaxeItem(MITHRIL_TIER,
            new Item.Properties().attributes(PickaxeItem.createAttributes(MITHRIL_TIER, 1.0F, -2.8F))));
    public static final DeferredItem<AxeItem> MITHRIL_AXE = ITEMS.register("mithril_axe", () -> new AxeItem(MITHRIL_TIER,
            new Item.Properties().attributes(AxeItem.createAttributes(MITHRIL_TIER, 5.0F, -3.0F))));
    public static final DeferredItem<ShovelItem> MITHRIL_SHOVEL = ITEMS.register("mithril_shovel", () -> new ShovelItem(MITHRIL_TIER,
            new Item.Properties().attributes(ShovelItem.createAttributes(MITHRIL_TIER, 1.5F, -3.0F))));
    public static final DeferredItem<HoeItem> MITHRIL_HOE = ITEMS.register("mithril_hoe", () -> new HoeItem(MITHRIL_TIER,
            new Item.Properties().attributes(HoeItem.createAttributes(MITHRIL_TIER, -3.5F, 0.0F))));
    /** Machado de Guerra Anão: golpe pesadíssimo e lento (11,5 de dano), derruba escudos como todo machado. */
    public static final DeferredItem<AxeItem> MITHRIL_WAR_AXE = ITEMS.register("mithril_war_axe", () -> new AxeItem(MITHRIL_TIER,
            new Item.Properties().rarity(Rarity.UNCOMMON).attributes(AxeItem.createAttributes(MITHRIL_TIER, 7.0F, -3.2F))));

    // ---------------- Armadura ----------------
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MITHRIL_MATERIAL = MATERIALS.register("mithril",
            () -> new ArmorMaterial(Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                m.put(ArmorItem.Type.BOOTS, 3);
                m.put(ArmorItem.Type.LEGGINGS, 6);
                m.put(ArmorItem.Type.CHESTPLATE, 8);
                m.put(ArmorItem.Type.HELMET, 3);
                m.put(ArmorItem.Type.BODY, 11);
            }), 18, SoundEvents.ARMOR_EQUIP_DIAMOND, () -> Ingredient.of(MITHRIL_INGOT.get()),
                    List.of(new ArmorMaterial.Layer(IsekaiMahou.id("mithril"))), 2.5F, 0.05F));
    public static final Map<ArmorItem.Type, DeferredItem<MithrilArmorItem>> ARMOR = new EnumMap<>(ArmorItem.Type.class);

    static {
        for (ArmorItem.Type type : List.of(ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS)) {
            ARMOR.put(type, ITEMS.register("mithril_" + type.getName(),
                    () -> new MithrilArmorItem(MITHRIL_MATERIAL, type, new Item.Properties().durability(type.getDurability(35)))));
        }
    }

    // ---------------- Tesouros de anime ----------------
    public static final DeferredItem<HeroShieldItem> HERO_SHIELD = ITEMS.register("hero_shield",
            () -> new HeroShieldItem(new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredItem<AppraisalMonocleItem> APPRAISAL_MONOCLE = ITEMS.register("appraisal_monocle",
            () -> new AppraisalMonocleItem(new Item.Properties().rarity(Rarity.RARE)));

    // ---------------- Anões ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<DwarfEntity>> DWARF_MERCHANT = ENTITIES.register("dwarf_merchant",
            () -> EntityType.Builder.<DwarfEntity>of((t, l) -> new DwarfEntity(t, l, DwarfEntity.Kind.MERCHANT), MobCategory.MISC)
                    .sized(0.7F, 1.4F).eyeHeight(1.2F).clientTrackingRange(10).build("dwarf_merchant"));
    public static final DeferredHolder<EntityType<?>, EntityType<DwarfEntity>> DWARF_SMITH = ENTITIES.register("dwarf_smith",
            () -> EntityType.Builder.<DwarfEntity>of((t, l) -> new DwarfEntity(t, l, DwarfEntity.Kind.SMITH), MobCategory.MISC)
                    .sized(0.7F, 1.4F).eyeHeight(1.2F).clientTrackingRange(10).build("dwarf_smith"));

    public static final DeferredItem<DeferredSpawnEggItem> DWARF_MERCHANT_EGG = ITEMS.register("dwarf_merchant_spawn_egg",
            () -> new DeferredSpawnEggItem(DWARF_MERCHANT, 0x7A4A2A, 0xD9A441, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> DWARF_SMITH_EGG = ITEMS.register("dwarf_smith_spawn_egg",
            () -> new DeferredSpawnEggItem(DWARF_SMITH, 0x4A3A30, 0xE0662A, new Item.Properties()));

    /** Itens da aba Mundo, na ordem. */
    public static List<DeferredItem<? extends Item>> worldTab() {
        return List.of(DEEPSLATE_MITHRIL_ORE_ITEM, RAW_MITHRIL, RAW_MITHRIL_BLOCK_ITEM, MITHRIL_NUGGET, MITHRIL_INGOT, MITHRIL_BLOCK_ITEM,
                RUNE_FORGE_ITEM, MITHRIL_SWORD, MITHRIL_WAR_AXE, MITHRIL_PICKAXE, MITHRIL_AXE, MITHRIL_SHOVEL, MITHRIL_HOE,
                ARMOR.get(ArmorItem.Type.HELMET), ARMOR.get(ArmorItem.Type.CHESTPLATE), ARMOR.get(ArmorItem.Type.LEGGINGS),
                ARMOR.get(ArmorItem.Type.BOOTS), HERO_SHIELD, APPRAISAL_MONOCLE);
    }

    public static Holder<ArmorMaterial> material() {
        return MITHRIL_MATERIAL;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        MATERIALS.register(modBus);
        COMPONENTS.register(modBus);
    }

    private DwarfRegistry() {}
}
