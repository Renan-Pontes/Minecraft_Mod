package com.renanpontes.isekaimahou.feature.towers;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.towers.block.FloorCoreBlock;
import com.renanpontes.isekaimahou.feature.towers.block.FloorCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.towers.block.RuneSwitchBlock;
import com.renanpontes.isekaimahou.feature.towers.entity.KoboldLord;
import com.renanpontes.isekaimahou.feature.towers.item.TeleportCrystalItem;
import com.renanpontes.isekaimahou.feature.towers.item.TwinBladeItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros das Torres de Desafio: blocos fixos da torre, tesouros de anime e o Lorde Kobold do Pináculo. */
public final class TwRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IsekaiMahou.MODID);

    // ---------------------------------------------------------------- blocos da torre (inquebráveis em survival)
    /** Selo (vidro arcano, como vidro: faces internas somem): tampa o buraco da escada; o Núcleo o remove ao vencer o andar. */
    public static final DeferredBlock<TransparentBlock> SEAL = BLOCKS.register("tower_seal",
            () -> new TransparentBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0F, 3600000.0F)
                    .noLootTable().noOcclusion().lightLevel(s -> 9).sound(SoundType.AMETHYST)
                    .isRedstoneConductor((s, l, p) -> false).isViewBlocking((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<FloorCoreBlock> FLOOR_CORE = BLOCKS.register("tower_floor_core",
            () -> new FloorCoreBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F)
                    .noLootTable().lightLevel(s -> 10).sound(SoundType.LODESTONE).pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<RuneSwitchBlock> RUNE_SWITCH = BLOCKS.register("tower_rune_switch",
            () -> new RuneSwitchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F)
                    .noLootTable().lightLevel(s -> s.getValue(RuneSwitchBlock.LIT) ? 12 : 2).sound(SoundType.STONE)
                    .pushReaction(PushReaction.BLOCK)));

    public static final DeferredItem<BlockItem> SEAL_ITEM = ITEMS.registerSimpleBlockItem(SEAL, new Item.Properties());
    public static final DeferredItem<BlockItem> FLOOR_CORE_ITEM = ITEMS.registerSimpleBlockItem(FLOOR_CORE,
            new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> RUNE_SWITCH_ITEM = ITEMS.registerSimpleBlockItem(RUNE_SWITCH, new Item.Properties());

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<FloorCoreBlockEntity>> FLOOR_CORE_BE = BLOCK_ENTITIES.register("tower_floor_core",
            () -> BlockEntityType.Builder.of(FloorCoreBlockEntity::new, FLOOR_CORE.get()).build(null));

    // ---------------------------------------------------------------- tesouros de anime
    /** Lâminas Gêmeas ≈ diamante (um pouco mais rápidas); o poder está no par. */
    public static final Tier TWIN_TIER = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1900, 8.0F, 3.5F, 16,
            () -> Ingredient.of(Items.DIAMOND));

    public static final DeferredItem<TeleportCrystalItem> TELEPORT_CRYSTAL = ITEMS.register("tower_teleport_crystal",
            () -> new TeleportCrystalItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<TwinBladeItem> TWIN_NOIR = ITEMS.register("tower_twin_blade_noir",
            () -> new TwinBladeItem(TWIN_TIER, 0x6C3AFF, new Item.Properties().rarity(Rarity.EPIC)
                    .attributes(SwordItem.createAttributes(TWIN_TIER, 3, -2.2F))));
    public static final DeferredItem<TwinBladeItem> TWIN_AZURE = ITEMS.register("tower_twin_blade_azure",
            () -> new TwinBladeItem(TWIN_TIER, 0x7FE8FF, new Item.Properties().rarity(Rarity.EPIC)
                    .attributes(SwordItem.createAttributes(TWIN_TIER, 3, -2.2F))));

    // ---------------------------------------------------------------- chefe
    public static final Supplier<EntityType<KoboldLord>> KOBOLD_LORD = ENTITIES.register("tower_kobold_lord",
            () -> EntityType.Builder.of(KoboldLord::new, MobCategory.MONSTER).sized(1.4F, 3.0F).eyeHeight(2.6F)
                    .clientTrackingRange(10).build("tower_kobold_lord"));
    public static final DeferredItem<DeferredSpawnEggItem> KOBOLD_LORD_EGG = ITEMS.register("tower_kobold_lord_spawn_egg",
            () -> new DeferredSpawnEggItem(KOBOLD_LORD, 0x5E3A20, 0xD9A632, new Item.Properties()));

    /** Aba "Mundo": blocos da torre (para construtores no criativo). */
    public static List<DeferredItem<? extends Item>> worldItems() {
        return List.of(FLOOR_CORE_ITEM, RUNE_SWITCH_ITEM, SEAL_ITEM);
    }

    /** Aba "Magia": tesouros. */
    public static List<DeferredItem<? extends Item>> gearItems() {
        return List.of(TELEPORT_CRYSTAL, TWIN_NOIR, TWIN_AZURE);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(KOBOLD_LORD.get(), KoboldLord.createAttributes().build()));
    }

    private TwRegistries() {}
}
