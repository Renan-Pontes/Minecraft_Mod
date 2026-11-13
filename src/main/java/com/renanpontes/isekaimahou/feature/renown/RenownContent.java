package com.renanpontes.isekaimahou.feature.renown;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.renown.block.RenownBoardBlock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do Renome: o bloco Quadro de Renome ({@code renown_board}) e o item dele. */
public final class RenownContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    public static final DeferredBlock<RenownBoardBlock> RENOWN_BOARD = BLOCKS.registerBlock("renown_board", RenownBoardBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
    public static final DeferredItem<BlockItem> RENOWN_BOARD_ITEM = ITEMS.registerSimpleBlockItem("renown_board", RENOWN_BOARD,
            new Item.Properties());

    private RenownContent() {}
}
