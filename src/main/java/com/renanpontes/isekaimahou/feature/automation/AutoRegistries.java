package com.renanpontes.isekaimahou.feature.automation;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.automation.block.ArcaneCollectorBlock;
import com.renanpontes.isekaimahou.feature.automation.block.ArcaneCollectorBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.block.AutomationBlockItem;
import com.renanpontes.isekaimahou.feature.automation.block.ManaBatteryBlock;
import com.renanpontes.isekaimahou.feature.automation.block.ManaBatteryBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.block.ManaBatteryItem;
import com.renanpontes.isekaimahou.feature.automation.block.ManaCondenserBlock;
import com.renanpontes.isekaimahou.feature.automation.block.ManaCondenserBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.block.ManaCondenserMenu;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlock;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalMenu;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo de Automação: os 4 blocos, seus itens, block entities, menus e o componente de mana da bateria. */
public final class AutoRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    /** Mana guardado numa Bateria quebrada (o item mantém a carga). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BATTERY_MANA = COMPONENTS.registerComponentType(
            "battery_mana", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    // ---------------------------------------------------------------- Blocos
    public static final DeferredBlock<RunicPedestalBlock> RUNIC_PEDESTAL = BLOCKS.registerBlock("runic_pedestal", RunicPedestalBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.0F, 9.0F).sound(SoundType.DEEPSLATE_BRICKS)
                    .requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK)
                    .lightLevel(s -> s.getValue(RunicPedestalBlock.ACTIVE) ? 9 : 3));

    public static final DeferredBlock<ManaBatteryBlock> MANA_BATTERY = BLOCKS.registerBlock("mana_battery", ManaBatteryBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.COPPER)
                    .requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)
                    .lightLevel(s -> s.getValue(ManaBatteryBlock.CHARGE) * 3));

    public static final DeferredBlock<ManaCondenserBlock> MANA_CONDENSER = BLOCKS.registerBlock("mana_condenser", ManaCondenserBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(3.5F, 6.0F).sound(SoundType.DEEPSLATE_TILES)
                    .requiresCorrectToolForDrops().lightLevel(s -> s.getValue(ManaCondenserBlock.LIT) ? 11 : 0));

    public static final DeferredBlock<ArcaneCollectorBlock> ARCANE_COLLECTOR = BLOCKS.registerBlock("arcane_collector", ArcaneCollectorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.0F, 6.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> 5));

    // ---------------------------------------------------------------- Itens dos blocos
    public static final DeferredItem<BlockItem> RUNIC_PEDESTAL_ITEM = ITEMS.register("runic_pedestal",
            () -> new AutomationBlockItem(RUNIC_PEDESTAL.get(), new Item.Properties().rarity(Rarity.UNCOMMON), 3));
    public static final DeferredItem<ManaBatteryItem> MANA_BATTERY_ITEM = ITEMS.register("mana_battery",
            () -> new ManaBatteryItem(MANA_BATTERY.get(), new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<BlockItem> MANA_CONDENSER_ITEM = ITEMS.register("mana_condenser",
            () -> new AutomationBlockItem(MANA_CONDENSER.get(), new Item.Properties(), 2));
    public static final DeferredItem<BlockItem> ARCANE_COLLECTOR_ITEM = ITEMS.register("arcane_collector",
            () -> new AutomationBlockItem(ARCANE_COLLECTOR.get(), new Item.Properties(), 2));

    // ---------------------------------------------------------------- Block entities
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<RunicPedestalBlockEntity>> RUNIC_PEDESTAL_BE = BLOCK_ENTITIES.register("runic_pedestal",
            () -> BlockEntityType.Builder.of(RunicPedestalBlockEntity::new, RUNIC_PEDESTAL.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<ManaBatteryBlockEntity>> MANA_BATTERY_BE = BLOCK_ENTITIES.register("mana_battery",
            () -> BlockEntityType.Builder.of(ManaBatteryBlockEntity::new, MANA_BATTERY.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<ManaCondenserBlockEntity>> MANA_CONDENSER_BE = BLOCK_ENTITIES.register("mana_condenser",
            () -> BlockEntityType.Builder.of(ManaCondenserBlockEntity::new, MANA_CONDENSER.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<ArcaneCollectorBlockEntity>> ARCANE_COLLECTOR_BE = BLOCK_ENTITIES.register("arcane_collector",
            () -> BlockEntityType.Builder.of(ArcaneCollectorBlockEntity::new, ARCANE_COLLECTOR.get()).build(null));

    // ---------------------------------------------------------------- Menus (o Coletor usa o baú 9×3 do vanilla)
    public static final Supplier<MenuType<RunicPedestalMenu>> RUNIC_PEDESTAL_MENU = MENUS.register("runic_pedestal",
            () -> IMenuTypeExtension.create(RunicPedestalMenu::new));
    public static final Supplier<MenuType<ManaCondenserMenu>> MANA_CONDENSER_MENU = MENUS.register("mana_condenser",
            () -> IMenuTypeExtension.create(ManaCondenserMenu::new));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
    }

    private AutoRegistries() {}
}
