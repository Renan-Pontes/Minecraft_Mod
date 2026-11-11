package com.renanpontes.isekaimahou.feature.ritual_druid;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ritual_druid.block.AncestralSaplingBlock;
import com.renanpontes.isekaimahou.feature.ritual_druid.block.AncestralSaplingBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_druid.block.HeartTreeCoreBlock;
import com.renanpontes.isekaimahou.feature.ritual_druid.block.HeartTreeCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_druid.block.HeartTreeLeavesBlock;
import com.renanpontes.isekaimahou.feature.ritual_druid.block.HeartTreeLogBlock;
import com.renanpontes.isekaimahou.feature.ritual_druid.entity.AnimalSpiritEntity;
import com.renanpontes.isekaimahou.feature.ritual_druid.item.AncestralSaplingItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do Ritual do Druida: Muda Ancestral, Árvore Coração (núcleo, tronco, folhas) e o Espírito Animal. */
public final class DruidContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);

    /** Blocos que contam como "corrupção" no bosque (data/isekai_mahou/tags/block/heart_tree_corruption.json). */
    public static final TagKey<Block> CORRUPTION = TagKey.create(Registries.BLOCK, IsekaiMahou.id("heart_tree_corruption"));

    // ---------------- Dados do jogador ----------------
    /** Onde está a Árvore Coração do jogador, base da tarefa de estação e mudas plantadas aguardando crescer. */
    public static final Supplier<AttachmentType<DruidGrove>> GROVE = ATTACHMENTS.register("ritual_druid_grove",
            () -> AttachmentType.builder(() -> new DruidGrove()).serialize(DruidGrove.CODEC).copyOnDeath().build());

    // ---------------- Blocos ----------------
    public static final DeferredBlock<AncestralSaplingBlock> ANCESTRAL_SAPLING = BLOCKS.registerBlock("ancestral_sapling",
            AncestralSaplingBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak()
                    .sound(SoundType.AZALEA).lightLevel(s -> 6).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<HeartTreeCoreBlock> HEART_TREE_CORE = BLOCKS.registerBlock("heart_tree_core",
            HeartTreeCoreBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(4.0F, 1200.0F)
                    .sound(SoundType.CHERRY_WOOD).lightLevel(s -> 11).pushReaction(PushReaction.BLOCK));
    public static final DeferredBlock<HeartTreeLogBlock> HEART_TREE_LOG = BLOCKS.registerBlock("heart_tree_log",
            HeartTreeLogBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(3.0F, 6.0F)
                    .sound(SoundType.CHERRY_WOOD).lightLevel(s -> 3));
    public static final DeferredBlock<HeartTreeLeavesBlock> HEART_TREE_LEAVES = BLOCKS.registerBlock("heart_tree_leaves",
            HeartTreeLeavesBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.3F)
                    .sound(SoundType.AZALEA_LEAVES).noOcclusion()
                    .lightLevel(s -> s.getValue(HeartTreeLeavesBlock.GOLDEN) ? 10 : 5)
                    .isValidSpawn((s, l, p, t) -> false).isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false)
                    .pushReaction(PushReaction.DESTROY));

    // ---------------- Itens ----------------
    public static final DeferredItem<AncestralSaplingItem> ANCESTRAL_SAPLING_ITEM = ITEMS.register("ancestral_sapling",
            () -> new AncestralSaplingItem(ANCESTRAL_SAPLING.get(), new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final DeferredItem<BlockItem> HEART_TREE_CORE_ITEM = ITEMS.registerSimpleBlockItem("heart_tree_core",
            HEART_TREE_CORE, new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<BlockItem> HEART_TREE_LOG_ITEM = ITEMS.registerSimpleBlockItem("heart_tree_log",
            HEART_TREE_LOG, new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<BlockItem> HEART_TREE_LEAVES_ITEM = ITEMS.registerSimpleBlockItem("heart_tree_leaves",
            HEART_TREE_LEAVES, new Item.Properties().rarity(Rarity.UNCOMMON));

    // ---------------- Block entities ----------------
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<AncestralSaplingBlockEntity>> ANCESTRAL_SAPLING_BE = BLOCK_ENTITIES.register(
            "ancestral_sapling", () -> BlockEntityType.Builder.of(AncestralSaplingBlockEntity::new, ANCESTRAL_SAPLING.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<HeartTreeCoreBlockEntity>> HEART_TREE_CORE_BE = BLOCK_ENTITIES.register(
            "heart_tree_core", () -> BlockEntityType.Builder.of(HeartTreeCoreBlockEntity::new, HEART_TREE_CORE.get()).build(null));

    // ---------------- Entidade: Espírito Animal ----------------
    public static final Supplier<EntityType<AnimalSpiritEntity>> ANIMAL_SPIRIT = ENTITIES.register("animal_spirit",
            () -> EntityType.Builder.<AnimalSpiritEntity>of(AnimalSpiritEntity::new, MobCategory.MISC)
                    .sized(0.9F, 1.7F).fireImmune().clientTrackingRange(10).updateInterval(2).build("animal_spirit"));
    public static final DeferredItem<DeferredSpawnEggItem> ANIMAL_SPIRIT_EGG = ITEMS.register("animal_spirit_spawn_egg",
            () -> new DeferredSpawnEggItem(ANIMAL_SPIRIT, 0xDDFBEF, 0xFFD86B, new Item.Properties()));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    private DruidContent() {}
}
