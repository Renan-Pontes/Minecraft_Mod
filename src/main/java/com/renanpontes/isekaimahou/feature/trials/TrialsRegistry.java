package com.renanpontes.isekaimahou.feature.trials;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.trials.block.TrialAltarBlock;
import com.renanpontes.isekaimahou.feature.trials.block.TrialExitBlock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.BarrierBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Blocos do módulo: Altar da Provação, Portal de Saída, Barreira invisível e a Pedra Rúnica decorativa. */
public final class TrialsRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    /** Indestrutível (como o portal do End): é a peça que move a provação. */
    public static final DeferredBlock<TrialAltarBlock> ALTAR = BLOCKS.registerBlock("trial_altar", TrialAltarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3600000.0F).noLootTable()
                    .sound(SoundType.AMETHYST).lightLevel(s -> 12).noOcclusion().pushReaction(PushReaction.BLOCK)
                    .isValidSpawn(Blocks::never));
    public static final DeferredBlock<TrialExitBlock> EXIT = BLOCKS.registerBlock("trial_exit", TrialExitBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0F, 3600000.0F).noLootTable()
                    .sound(SoundType.AMETHYST).lightLevel(s -> 14).noOcclusion().pushReaction(PushReaction.BLOCK)
                    .isValidSpawn(Blocks::never));
    /** Parede invisível das arenas (mesmo comportamento da barreira vanilla, mas com nome/id próprios). */
    public static final DeferredBlock<BarrierBlock> BARRIER = BLOCKS.registerBlock("trial_barrier", BarrierBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(-1.0F, 3600000.8F).noLootTable().noOcclusion()
                    .isValidSpawn(Blocks::never).noTerrainParticles().pushReaction(PushReaction.BLOCK)
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    public static final DeferredBlock<Block> RUNE_STONE = BLOCKS.registerSimpleBlock("trial_rune_stone",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1.5F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE_BRICKS).lightLevel(s -> 9));

    public static final DeferredItem<BlockItem> ALTAR_ITEM = ITEMS.registerSimpleBlockItem("trial_altar", ALTAR,
            new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<BlockItem> EXIT_ITEM = ITEMS.registerSimpleBlockItem("trial_exit", EXIT,
            new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> BARRIER_ITEM = ITEMS.registerSimpleBlockItem("trial_barrier", BARRIER,
            new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<BlockItem> RUNE_STONE_ITEM = ITEMS.registerSimpleBlockItem("trial_rune_stone", RUNE_STONE,
            new Item.Properties());

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }

    private TrialsRegistry() {}
}
