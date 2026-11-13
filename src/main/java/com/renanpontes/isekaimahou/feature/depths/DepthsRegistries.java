package com.renanpontes.isekaimahou.feature.depths;

import java.util.List;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.depths.block.ManaBuddingBlock;
import com.renanpontes.isekaimahou.feature.depths.entity.DepthsBurrower;
import com.renanpontes.isekaimahou.feature.depths.entity.LostDelver;
import com.renanpontes.isekaimahou.feature.depths.entity.WormKing;
import com.renanpontes.isekaimahou.feature.depths.item.DepthsLoreItem;
import com.renanpontes.isekaimahou.feature.depths.item.DragonslayerItem;
import com.renanpontes.isekaimahou.feature.depths.item.GoddessKnifeItem;
import com.renanpontes.isekaimahou.feature.depths.item.PureManaShardItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros das Profundezas: Geodos de Mana (blocos), tesouros de anime, Verme-Rei e as criaturas da masmorra. */
public final class DepthsRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IsekaiMahou.MODID);

    // ---------------------------------------------------------------- componente
    /** "Falna" da Faca da Deusa: bônus de dano atual ×10 (só para o tooltip; o dano real é calculado na hora). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FALNA = COMPONENTS.registerComponentType(
            "depths_falna", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    // ---------------------------------------------------------------- Geodo de Mana
    public static final DeferredBlock<Block> MANA_GEODE_BLOCK = BLOCKS.register("depths_mana_geode_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5F)
                    .sound(SoundType.AMETHYST).lightLevel(s -> 4).requiresCorrectToolForDrops()));
    public static final DeferredBlock<ManaBuddingBlock> BUDDING_MANA_CRYSTAL = BLOCKS.register("depths_budding_mana_crystal",
            () -> new ManaBuddingBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).randomTicks().strength(1.5F)
                    .sound(SoundType.AMETHYST).lightLevel(s -> 5).requiresCorrectToolForDrops().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<AmethystClusterBlock> MANA_BUD = BLOCKS.register("depths_mana_bud",
            () -> new AmethystClusterBlock(4.0F, 3.0F, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).forceSolidOn()
                    .noOcclusion().strength(1.5F).sound(SoundType.MEDIUM_AMETHYST_BUD).lightLevel(s -> 3).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<AmethystClusterBlock> MANA_CLUSTER = BLOCKS.register("depths_mana_cluster",
            () -> new AmethystClusterBlock(7.0F, 3.0F, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).forceSolidOn()
                    .noOcclusion().strength(1.5F).sound(SoundType.AMETHYST_CLUSTER).lightLevel(s -> 7).pushReaction(PushReaction.DESTROY)));

    public static final DeferredItem<BlockItem> MANA_GEODE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(MANA_GEODE_BLOCK);
    public static final DeferredItem<BlockItem> BUDDING_MANA_CRYSTAL_ITEM = ITEMS.registerSimpleBlockItem(BUDDING_MANA_CRYSTAL,
            new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> MANA_BUD_ITEM = ITEMS.registerSimpleBlockItem(MANA_BUD);
    public static final DeferredItem<BlockItem> MANA_CLUSTER_ITEM = ITEMS.registerSimpleBlockItem(MANA_CLUSTER,
            new Item.Properties().rarity(Rarity.UNCOMMON));

    // ---------------------------------------------------------------- materiais e tesouros
    public static final DeferredItem<PureManaShardItem> PURE_MANA_SHARD = ITEMS.register("depths_pure_mana_shard",
            () -> new PureManaShardItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<DepthsLoreItem> WORM_KING_FANG = ITEMS.register("depths_worm_king_fang",
            () -> new DepthsLoreItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), true));
    public static final DeferredItem<DragonslayerItem> DRAGONSLAYER = ITEMS.register("depths_dragonslayer",
            () -> new DragonslayerItem(Tiers.NETHERITE, new Item.Properties().fireResistant().rarity(Rarity.EPIC)
                    .attributes(DragonslayerItem.createDragonslayerAttributes())));
    public static final DeferredItem<GoddessKnifeItem> GODDESS_KNIFE = ITEMS.register("depths_goddess_knife",
            () -> new GoddessKnifeItem(Tiers.NETHERITE, new Item.Properties().fireResistant().rarity(Rarity.EPIC)
                    .attributes(SwordItem.createAttributes(Tiers.NETHERITE, -1, -2.0F))));

    // ---------------------------------------------------------------- entidades
    public static final Supplier<EntityType<WormKing>> WORM_KING = ENTITIES.register("depths_worm_king",
            () -> EntityType.Builder.of(WormKing::new, MobCategory.MONSTER).sized(2.4F, 5.0F).eyeHeight(4.4F)
                    .clientTrackingRange(10).build("depths_worm_king"));
    public static final Supplier<EntityType<DepthsBurrower>> BURROWER = ENTITIES.register("depths_burrower",
            () -> EntityType.Builder.of(DepthsBurrower::new, MobCategory.MONSTER).sized(0.7F, 0.5F).eyeHeight(0.35F)
                    .clientTrackingRange(8).build("depths_burrower"));
    public static final Supplier<EntityType<LostDelver>> LOST_DELVER = ENTITIES.register("depths_lost_delver",
            () -> EntityType.Builder.of(LostDelver::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.7F)
                    .clientTrackingRange(8).build("depths_lost_delver"));

    public static final DeferredItem<DeferredSpawnEggItem> WORM_KING_EGG = egg("depths_worm_king", WORM_KING, 0x6A4A32, 0x5AE0FF);
    public static final DeferredItem<DeferredSpawnEggItem> BURROWER_EGG = egg("depths_burrower", BURROWER, 0xB07868, 0x5E4636);
    public static final DeferredItem<DeferredSpawnEggItem> LOST_DELVER_EGG = egg("depths_lost_delver", LOST_DELVER, 0xD8CFB8, 0xC8A03A);

    private static DeferredItem<DeferredSpawnEggItem> egg(String id, Supplier<? extends EntityType<? extends Mob>> type, int back, int spots) {
        return ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(type, back, spots, new Item.Properties()));
    }

    public static List<DeferredItem<DeferredSpawnEggItem>> eggs() {
        return List.of(WORM_KING_EGG, BURROWER_EGG, LOST_DELVER_EGG);
    }

    /** Aba "Mundo": blocos do geodo e o fragmento. */
    public static List<DeferredItem<? extends Item>> worldItems() {
        return List.of(MANA_GEODE_BLOCK_ITEM, BUDDING_MANA_CRYSTAL_ITEM, MANA_BUD_ITEM, MANA_CLUSTER_ITEM, PURE_MANA_SHARD);
    }

    /** Aba "Magia": tesouros e o troféu do chefe. */
    public static List<DeferredItem<? extends Item>> gearItems() {
        return List.of(DRAGONSLAYER, GODDESS_KNIFE, WORM_KING_FANG);
    }

    /**
     * Criaturas das profundezas: no spawn natural só nascem ABAIXO de y 0 (cavernas fundas); nos geradores da
     * masmorra (regras próprias de luz) e por ovo/comando, em qualquer altura.
     */
    private static boolean deepSpawn(EntityType<? extends Monster> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                     RandomSource random) {
        if (reason == MobSpawnType.NATURAL && pos.getY() >= 0) return false;
        return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(WORM_KING.get(), WormKing.createAttributes().build());
            e.put(BURROWER.get(), DepthsBurrower.createAttributes().build());
            e.put(LOST_DELVER.get(), LostDelver.createAttributes().build());
        });
        modBus.addListener((RegisterSpawnPlacementsEvent e) -> {
            e.register(BURROWER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    DepthsRegistries::deepSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
            e.register(LOST_DELVER.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    DepthsRegistries::deepSpawn, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        });
    }

    private DepthsRegistries() {}
}
