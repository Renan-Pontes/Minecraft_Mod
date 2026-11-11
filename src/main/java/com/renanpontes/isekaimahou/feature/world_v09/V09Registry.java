package com.renanpontes.isekaimahou.feature.world_v09;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.world_v09.block.ManaCrystalClusterBlock;
import com.renanpontes.isekaimahou.feature.world_v09.block.ManaVeinOreBlock;
import com.renanpontes.isekaimahou.feature.world_v09.world.ManaCrystalSiteFeature;

import net.minecraft.core.registries.Registries;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do world_v09: Veio de Mana (minério), Aglomerado de Cristal de Mana e a feature dos aglomerados. */
public final class V09Registry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, IsekaiMahou.MODID);

    // ---------------- Blocos ----------------
    /** Veio de Mana na pedra: 1–3 Cristais de Mana (Fortuna), XP como o lápis. Brilha de leve nas cavernas. */
    public static final DeferredBlock<ManaVeinOreBlock> MANA_VEIN_ORE = BLOCKS.registerBlock("mana_vein_ore",
            p -> new ManaVeinOreBlock(UniformInt.of(2, 5), p),
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE).mapColor(MapColor.STONE).lightLevel(s -> 3));
    public static final DeferredBlock<ManaVeinOreBlock> DEEPSLATE_MANA_VEIN_ORE = BLOCKS.registerBlock("deepslate_mana_vein_ore",
            p -> new ManaVeinOreBlock(UniformInt.of(3, 6), p),
            BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE).mapColor(MapColor.DEEPSLATE).lightLevel(s -> 3));
    /** Aglomerado decorativo (perto de santuários e no Santuário de Ascensão): quebrado dá Cristais de Mana. */
    public static final DeferredBlock<ManaCrystalClusterBlock> MANA_CRYSTAL_CLUSTER = BLOCKS.registerBlock("mana_crystal_cluster",
            ManaCrystalClusterBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER).mapColor(MapColor.COLOR_PURPLE).lightLevel(s -> 7));

    // ---------------- Itens dos blocos ----------------
    public static final DeferredItem<BlockItem> MANA_VEIN_ORE_ITEM = ITEMS.registerSimpleBlockItem("mana_vein_ore", MANA_VEIN_ORE,
            new Item.Properties());
    public static final DeferredItem<BlockItem> DEEPSLATE_MANA_VEIN_ORE_ITEM = ITEMS.registerSimpleBlockItem("deepslate_mana_vein_ore",
            DEEPSLATE_MANA_VEIN_ORE, new Item.Properties());
    public static final DeferredItem<BlockItem> MANA_CRYSTAL_CLUSTER_ITEM = ITEMS.registerSimpleBlockItem("mana_crystal_cluster",
            MANA_CRYSTAL_CLUSTER, new Item.Properties());

    // ---------------- Worldgen ----------------
    /** Aglomerados de cristal nos chunks que tocam santuários/portais (tag {@code #isekai_mahou:mana_crystal_sites}). */
    public static final DeferredHolder<Feature<?>, ManaCrystalSiteFeature> MANA_CRYSTAL_SITE = FEATURES.register("mana_crystal_site",
            () -> new ManaCrystalSiteFeature(NoneFeatureConfiguration.CODEC));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        FEATURES.register(modBus);
    }

    private V09Registry() {}
}
