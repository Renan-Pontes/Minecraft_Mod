package com.renanpontes.isekaimahou.feature.magic_biomes;

import java.util.List;
import java.util.function.ToIntFunction;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Blocos dos biomas mágicos: três aglomerados de cristal que brilham (ciano = Floresta/Caverna de Cristal, laranja =
 * Nether, violeta = End e Bosque Crepuscular), o Bloco de Cristal Luminoso e as Folhas de Cristal.
 * Modelos/texturas/loot: {@code tools/gen/magic_biomes.py}.
 */
public final class MagicBiomesContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    public static final DeferredBlock<AmethystClusterBlock> LUMINOUS_CLUSTER = cluster("luminous_crystal_cluster", MapColor.DIAMOND, 12);
    public static final DeferredBlock<AmethystClusterBlock> EMBER_CLUSTER = cluster("ember_crystal_cluster", MapColor.COLOR_ORANGE, 13);
    public static final DeferredBlock<AmethystClusterBlock> ASTRAL_CLUSTER = cluster("astral_crystal_cluster", MapColor.COLOR_PURPLE, 11);
    public static final DeferredBlock<Block> CRYSTAL_BLOCK = BLOCKS.registerSimpleBlock("luminous_crystal_block",
            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).mapColor(MapColor.DIAMOND).lightLevel(light(9)));
    public static final DeferredBlock<LeavesBlock> LEAVES = BLOCKS.registerBlock("luminous_leaves", LeavesBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BIRCH_LEAVES).mapColor(MapColor.COLOR_LIGHT_BLUE).lightLevel(light(4)));

    public static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = List.of(
            item(LUMINOUS_CLUSTER), item(EMBER_CLUSTER), item(ASTRAL_CLUSTER), item(CRYSTAL_BLOCK), item(LEAVES));

    private static DeferredBlock<AmethystClusterBlock> cluster(String id, MapColor color, int light) {
        return BLOCKS.registerBlock(id, p -> new AmethystClusterBlock(7.0F, 3.0F, p),
                BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER).mapColor(color).lightLevel(light(light)));
    }

    private static ToIntFunction<BlockState> light(int level) {
        return s -> level;
    }

    private static DeferredItem<BlockItem> item(DeferredBlock<? extends Block> block) {
        return ITEMS.registerSimpleBlockItem(block.getId().getPath(), block, new Item.Properties());
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    private MagicBiomesContent() {}
}
