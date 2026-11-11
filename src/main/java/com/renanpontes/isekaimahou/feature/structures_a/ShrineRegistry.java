package com.renanpontes.isekaimahou.feature.structures_a;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.structures_a.block.ElementalBrickBlock;
import com.renanpontes.isekaimahou.feature.structures_a.block.ShrineAltarBlock;
import com.renanpontes.isekaimahou.feature.structures_a.block.ShrineAltarBlockEntity;
import com.renanpontes.isekaimahou.feature.structures_a.block.ShrineAltarItem;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo: o Altar do Santuário (com block entity) e os seis tijolos elementais. */
public final class ShrineRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IsekaiMahou.MODID);

    /** Altar: indestrutível no modo sobrevivência (como o portal do End), para o santuário não ser saqueado. */
    public static final DeferredBlock<ShrineAltarBlock> ALTAR = BLOCKS.registerBlock("shrine_altar", ShrineAltarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).noLootTable()
                    .sound(SoundType.DEEPSLATE_BRICKS).lightLevel(s -> 9).noOcclusion().pushReaction(PushReaction.BLOCK)
                    .isValidSpawn(Blocks::never));
    public static final DeferredItem<ShrineAltarItem> ALTAR_ITEM = ITEMS.register("shrine_altar",
            () -> new ShrineAltarItem(ALTAR.get(), new Item.Properties().rarity(Rarity.EPIC)));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<ShrineAltarBlockEntity>> ALTAR_BE = BLOCK_ENTITIES.register("shrine_altar",
            () -> BlockEntityType.Builder.of(ShrineAltarBlockEntity::new, ALTAR.get()).build(null));

    public static final Map<Element, DeferredBlock<ElementalBrickBlock>> BRICKS = new EnumMap<>(Element.class);
    public static final Map<Element, DeferredItem<BlockItem>> BRICK_ITEMS = new EnumMap<>(Element.class);

    static {
        for (Element e : Element.BASE) {
            String id = "elemental_brick_" + e.id;
            DeferredBlock<ElementalBrickBlock> block = BLOCKS.register(id, () -> new ElementalBrickBlock(e, brickProps(e)));
            BRICKS.put(e, block);
            BRICK_ITEMS.put(e, ITEMS.registerSimpleBlockItem(id, block, new Item.Properties()));
        }
    }

    private static BlockBehaviour.Properties brickProps(Element e) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().strength(2.0F, 6.0F).requiresCorrectToolForDrops();
        return switch (e) {
            case FIRE -> p.mapColor(MapColor.NETHER).sound(SoundType.NETHER_BRICKS).lightLevel(s -> 3);
            case WATER -> p.mapColor(MapColor.COLOR_CYAN).sound(SoundType.STONE);
            case EARTH -> p.mapColor(MapColor.DIRT).sound(SoundType.MUD_BRICKS);
            case WIND -> p.mapColor(MapColor.SNOW).sound(SoundType.CALCITE);
            case LIGHT -> p.mapColor(MapColor.SAND).sound(SoundType.STONE).lightLevel(s -> 6);
            default -> p.mapColor(MapColor.COLOR_BLACK).sound(SoundType.DEEPSLATE_BRICKS);
        };
    }

    /** Item do altar já com o elemento escolhido (o estado do bloco vai no componente block_state). */
    public static ItemStack altarStack(Element e) {
        ItemStack stack = new ItemStack(ALTAR_ITEM.get());
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(ShrineAltarBlock.ELEMENT, e));
        return stack;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }

    private ShrineRegistry() {}
}
