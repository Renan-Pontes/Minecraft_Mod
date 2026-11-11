package com.renanpontes.isekaimahou.feature.farm;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.farm.blueprint.BlueprintItem;
import com.renanpontes.isekaimahou.feature.farm.blueprint.BlueprintKind;
import com.renanpontes.isekaimahou.feature.farm.blueprint.ConstructionSiteBlock;
import com.renanpontes.isekaimahou.feature.farm.blueprint.ConstructionSiteBlockEntity;
import com.renanpontes.isekaimahou.feature.farm.machine.ElementalPedestalBlock;
import com.renanpontes.isekaimahou.feature.farm.machine.ElementalPedestalItem;
import com.renanpontes.isekaimahou.feature.farm.machine.FarmMachineBlock;
import com.renanpontes.isekaimahou.feature.farm.machine.FarmMachineBlockEntity;
import com.renanpontes.isekaimahou.feature.farm.machine.FarmMachineItem;
import com.renanpontes.isekaimahou.feature.farm.machine.FarmMachineMenu;
import com.renanpontes.isekaimahou.feature.farm.machine.MachineKind;
import com.renanpontes.isekaimahou.feature.farm.wand.ConstructionWandItem;
import com.renanpontes.isekaimahou.feature.farm.wand.WandData;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
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
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo "farm": varinha, plantas de casa, canteiro de obras, as 4 fazendas mágicas e o Pedestal Elemental. */
public final class FarmRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    /** Estado da Varinha de Construção (modo, cantos, material, baú ligado, tamanho da área copiada). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WandData>> WAND = COMPONENTS.registerComponentType(
            "farm_wand", b -> b.persistent(WandData.CODEC).networkSynchronized(WandData.STREAM_CODEC));

    // ---------------------------------------------------------------- Construção
    public static final DeferredItem<ConstructionWandItem> CONSTRUCTION_WAND = ITEMS.register("construction_wand",
            () -> new ConstructionWandItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).component(WAND.get(), WandData.EMPTY)));

    public static final DeferredBlock<ConstructionSiteBlock> CONSTRUCTION_SITE = BLOCKS.registerBlock("construction_site", ConstructionSiteBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F, 3.0F).sound(SoundType.WOOD).noOcclusion()
                    .noLootTable().pushReaction(PushReaction.BLOCK).lightLevel(s -> 6).isValidSpawn(Blocks::never));

    public static final Map<BlueprintKind, DeferredItem<BlueprintItem>> BLUEPRINTS = new EnumMap<>(BlueprintKind.class);

    // ---------------------------------------------------------------- Fazendas mágicas
    public static final Map<MachineKind, DeferredBlock<? extends FarmMachineBlock>> MACHINES = new EnumMap<>(MachineKind.class);
    public static final Map<MachineKind, DeferredItem<? extends Item>> MACHINE_ITEMS = new EnumMap<>(MachineKind.class);

    static {
        for (BlueprintKind k : BlueprintKind.values()) {
            BLUEPRINTS.put(k, ITEMS.register(k.itemId(), () -> new BlueprintItem(k, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON))));
        }
        for (MachineKind k : MachineKind.values()) {
            if (k == MachineKind.ESSENCE) {
                DeferredBlock<ElementalPedestalBlock> block = BLOCKS.registerBlock(k.id, p -> new ElementalPedestalBlock(k, p), k.properties());
                MACHINES.put(k, block);
                MACHINE_ITEMS.put(k, ITEMS.register(k.id, () -> new ElementalPedestalItem(block.get(), new Item.Properties().rarity(Rarity.EPIC))));
            } else {
                DeferredBlock<FarmMachineBlock> block = BLOCKS.registerBlock(k.id, p -> new FarmMachineBlock(k, p), k.properties());
                MACHINES.put(k, block);
                MACHINE_ITEMS.put(k, ITEMS.register(k.id, () -> new FarmMachineItem(block.get(), k, new Item.Properties().rarity(Rarity.UNCOMMON))));
            }
        }
    }

    // ---------------------------------------------------------------- Block entities e menu
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<FarmMachineBlockEntity>> MACHINE_BE = BLOCK_ENTITIES.register("farm_machine",
            () -> BlockEntityType.Builder.of(FarmMachineBlockEntity::new, MACHINES.values().stream().map(Supplier::get)
                    .toArray(net.minecraft.world.level.block.Block[]::new)).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<ConstructionSiteBlockEntity>> SITE_BE = BLOCK_ENTITIES.register("construction_site",
            () -> BlockEntityType.Builder.of(ConstructionSiteBlockEntity::new, CONSTRUCTION_SITE.get()).build(null));

    public static final Supplier<MenuType<FarmMachineMenu>> MACHINE_MENU = MENUS.register("farm_machine",
            () -> IMenuTypeExtension.create(FarmMachineMenu::new));

    public static DeferredBlock<? extends FarmMachineBlock> machine(MachineKind k) {
        return MACHINES.get(k);
    }

    /** Item do Pedestal Elemental já com o elemento (estado do bloco no componente block_state). */
    public static ItemStack pedestalStack(Element e) {
        ItemStack stack = new ItemStack(MACHINE_ITEMS.get(MachineKind.ESSENCE).get());
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(ElementalPedestalBlock.ELEMENT, e));
        return stack;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        COMPONENTS.register(modBus);
    }

    private FarmRegistries() {}
}
