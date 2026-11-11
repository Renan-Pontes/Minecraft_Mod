package com.renanpontes.isekaimahou.feature.travel;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.travel.block.TravelStoneBlock;
import com.renanpontes.isekaimahou.feature.travel.block.TravelStoneBlockEntity;
import com.renanpontes.isekaimahou.feature.travel.block.TravelStoneItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo travel: a Pedra de Teleporte (bloco, item e block entity). */
public final class TravelContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IsekaiMahou.MODID);

    /**
     * Resistência alta e picareta para a pedra feita pelo jogador; a pedra das estruturas (sem dono) não quebra em
     * sobrevivência (ver {@link TravelStoneBlock#getDestroyProgress}).
     */
    public static final DeferredBlock<TravelStoneBlock> TRAVEL_STONE = BLOCKS.registerBlock("travel_stone", TravelStoneBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(6.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE_BRICKS).lightLevel(s -> 10).noOcclusion().pushReaction(PushReaction.BLOCK));

    public static final DeferredItem<BlockItem> TRAVEL_STONE_ITEM = ITEMS.register("travel_stone",
            () -> new TravelStoneItem(TRAVEL_STONE.get(), new Item.Properties().rarity(Rarity.RARE)));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<TravelStoneBlockEntity>> TRAVEL_STONE_BE = BLOCK_ENTITIES.register("travel_stone",
            () -> BlockEntityType.Builder.of(TravelStoneBlockEntity::new, TRAVEL_STONE.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }

    private TravelContent() {}
}
