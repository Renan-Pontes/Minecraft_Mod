package com.renanpontes.isekaimahou.feature.depths.puzzle;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Peças dos <b>quebra-cabeças</b> das masmorras (v1.0): Selo Rúnico (o "cérebro"), Lâmpada, Placa, Pedra Rúnica,
 * Espelho e Grade Selada. São indestrutíveis no survival (o enigma não pode ser quebrado na picareta) e ficam na aba
 * "Mundo" do criativo para quem quiser montar os seus. As outras masmorras usam estes blocos só pelo id, nos moldes.
 */
public final class PuzzleBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IsekaiMahou.MODID);

    private static BlockBehaviour.Properties sealed(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0F, 3600000.0F).noLootTable()
                .pushReaction(PushReaction.BLOCK).sound(SoundType.DEEPSLATE_TILES);
    }

    public static final DeferredBlock<PuzzleCoreBlock> CORE = BLOCKS.register("depths_puzzle_core",
            () -> new PuzzleCoreBlock(sealed(MapColor.COLOR_PURPLE).lightLevel(s -> s.getValue(PuzzleCoreBlock.SOLVED) ? 12 : 6)));
    public static final DeferredBlock<RuneLitBlock> LAMP = BLOCKS.register("depths_rune_lamp",
            () -> new RuneLitBlock(sealed(MapColor.COLOR_CYAN).lightLevel(s -> s.getValue(RuneLitBlock.LIT) ? 13 : 0)));
    public static final DeferredBlock<RuneLitBlock> TILE = BLOCKS.register("depths_rune_tile",
            () -> new RuneLitBlock(sealed(MapColor.DEEPSLATE).lightLevel(s -> s.getValue(RuneLitBlock.LIT) ? 9 : 0)));
    public static final DeferredBlock<RuneStoneBlock> STONE = BLOCKS.register("depths_rune_stone",
            () -> new RuneStoneBlock(sealed(MapColor.DEEPSLATE).lightLevel(s -> s.getValue(RuneLitBlock.LIT) ? 11 : 2)));
    public static final DeferredBlock<LightMirrorBlock> MIRROR = BLOCKS.register("depths_light_mirror",
            () -> new LightMirrorBlock(sealed(MapColor.METAL).noOcclusion().sound(SoundType.AMETHYST)));
    public static final DeferredBlock<TransparentBlock> GATE = BLOCKS.register("depths_puzzle_gate",
            () -> new TransparentBlock(sealed(MapColor.METAL).noOcclusion().sound(SoundType.CHAIN)
                    .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));

    public static final DeferredItem<BlockItem> CORE_ITEM = ITEMS.registerSimpleBlockItem(CORE);
    public static final DeferredItem<BlockItem> LAMP_ITEM = ITEMS.registerSimpleBlockItem(LAMP);
    public static final DeferredItem<BlockItem> TILE_ITEM = ITEMS.registerSimpleBlockItem(TILE);
    public static final DeferredItem<BlockItem> STONE_ITEM = ITEMS.registerSimpleBlockItem(STONE);
    public static final DeferredItem<BlockItem> MIRROR_ITEM = ITEMS.registerSimpleBlockItem(MIRROR);
    public static final DeferredItem<BlockItem> GATE_ITEM = ITEMS.registerSimpleBlockItem(GATE);

    public static final Supplier<BlockEntityType<PuzzleCoreBlockEntity>> CORE_BE = BLOCK_ENTITIES.register("depths_puzzle_core",
            () -> BlockEntityType.Builder.of(PuzzleCoreBlockEntity::new, CORE.get()).build(null));

    public static List<DeferredItem<? extends Item>> items() {
        return List.of(CORE_ITEM, LAMP_ITEM, TILE_ITEM, STONE_ITEM, MIRROR_ITEM, GATE_ITEM);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }

    private PuzzleBlocks() {}
}
