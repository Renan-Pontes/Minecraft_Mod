package com.renanpontes.isekaimahou.feature.market;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registro do módulo: o Quadro do Mercado (bloco + item). */
public final class MarketContent {
    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    public static final DeferredBlock<MarketBoardBlock> MARKET_BOARD = BLOCKS.registerBlock("market_board", MarketBoardBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
    public static final DeferredItem<BlockItem> MARKET_BOARD_ITEM = ITEMS.registerSimpleBlockItem("market_board", MARKET_BOARD,
            new Item.Properties());

    private MarketContent() {}
}
