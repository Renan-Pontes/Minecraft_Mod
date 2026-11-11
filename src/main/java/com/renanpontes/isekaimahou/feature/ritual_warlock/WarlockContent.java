package com.renanpontes.isekaimahou.feature.ritual_warlock;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ritual_warlock.block.OfferingAltarBlock;
import com.renanpontes.isekaimahou.feature.ritual_warlock.block.OfferingAltarBlockEntity;
import com.renanpontes.isekaimahou.feature.ritual_warlock.block.RitualChalkBlock;
import com.renanpontes.isekaimahou.feature.ritual_warlock.entity.DrownedKingEntity;
import com.renanpontes.isekaimahou.feature.ritual_warlock.entity.EmberLordEntity;
import com.renanpontes.isekaimahou.feature.ritual_warlock.entity.VoidLadyEntity;
import com.renanpontes.isekaimahou.feature.ritual_warlock.item.RitualChalkItem;
import com.renanpontes.isekaimahou.feature.ritual_warlock.item.RitualDaggerItem;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PactState;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do Ritual do Warlock: giz, altar, adaga, Patronos e o estado do pacto do jogador. */
public final class WarlockContent {
    private static final String MODID = IsekaiMahou.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MODID);

    // ---------------- Blocos ----------------
    /** Desenho de giz no chão (fino como tapete, sem colisão). Apagar o desenho não devolve giz. */
    public static final DeferredBlock<RitualChalkBlock> RITUAL_CHALK = BLOCKS.registerBlock("ritual_chalk", RitualChalkBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).noCollission().instabreak().noLootTable().sound(SoundType.CALCITE)
                    .pushReaction(PushReaction.DESTROY).noOcclusion()
                    .lightLevel(s -> s.getValue(RitualChalkBlock.BLOOD) ? 4 : 0));

    public static final DeferredBlock<OfferingAltarBlock> OFFERING_ALTAR = BLOCKS.registerBlock("offering_altar", OfferingAltarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(3.0F, 9.0F)
                    .sound(SoundType.POLISHED_DEEPSLATE).noOcclusion().lightLevel(s -> 3));
    public static final DeferredItem<BlockItem> OFFERING_ALTAR_ITEM = ITEMS.registerSimpleBlockItem("offering_altar", OFFERING_ALTAR,
            new Item.Properties().rarity(Rarity.UNCOMMON));

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<OfferingAltarBlockEntity>> OFFERING_ALTAR_BE = BLOCK_ENTITIES.register("offering_altar",
            () -> BlockEntityType.Builder.of(OfferingAltarBlockEntity::new, OFFERING_ALTAR.get()).build(null));

    // ---------------- Itens ----------------
    public static final DeferredItem<RitualChalkItem> RITUAL_CHALK_ITEM = ITEMS.register("ritual_chalk_item",
            () -> new RitualChalkItem(new Item.Properties().durability(64)));
    public static final DeferredItem<RitualDaggerItem> RITUAL_DAGGER = ITEMS.register("ritual_dagger",
            () -> new RitualDaggerItem(Tiers.IRON, new Item.Properties().rarity(Rarity.UNCOMMON)
                    .attributes(RitualDaggerItem.createAttributes(Tiers.IRON, 1, -1.6F))));

    // ---------------- Patronos ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<EmberLordEntity>> EMBER_LORD = ENTITIES.register("patron_ember_lord",
            () -> EntityType.Builder.of(EmberLordEntity::new, MobCategory.MONSTER).sized(1.5F, 3.3F).fireImmune()
                    .clientTrackingRange(10).build("patron_ember_lord"));
    public static final DeferredHolder<EntityType<?>, EntityType<VoidLadyEntity>> VOID_LADY = ENTITIES.register("patron_void_lady",
            () -> EntityType.Builder.of(VoidLadyEntity::new, MobCategory.MONSTER).sized(1.1F, 3.0F)
                    .clientTrackingRange(10).build("patron_void_lady"));
    public static final DeferredHolder<EntityType<?>, EntityType<DrownedKingEntity>> DROWNED_KING = ENTITIES.register("patron_drowned_king",
            () -> EntityType.Builder.of(DrownedKingEntity::new, MobCategory.MONSTER).sized(1.6F, 3.0F)
                    .clientTrackingRange(10).build("patron_drowned_king"));

    public static final DeferredItem<DeferredSpawnEggItem> EMBER_LORD_EGG = ITEMS.register("patron_ember_lord_spawn_egg",
            () -> new DeferredSpawnEggItem(EMBER_LORD, 0x2A1414, 0xFF7A1A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VOID_LADY_EGG = ITEMS.register("patron_void_lady_spawn_egg",
            () -> new DeferredSpawnEggItem(VOID_LADY, 0x1A0F2A, 0xB070FF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> DROWNED_KING_EGG = ITEMS.register("patron_drowned_king_spawn_egg",
            () -> new DeferredSpawnEggItem(DROWNED_KING, 0x2D5E52, 0xFF7F6A, new Item.Properties()));

    // ---------------- Estado do pacto do jogador (salvo, sobrevive à morte) ----------------
    public static final Supplier<AttachmentType<PactState>> PACT = ATTACHMENTS.register("warlock_pact",
            () -> AttachmentType.builder(PactState::new).serialize(PactState.CODEC).copyOnDeath().build());

    public static List<Item> ritualTabItems() {
        return List.of(RITUAL_CHALK_ITEM.get(), RITUAL_DAGGER.get(), OFFERING_ALTAR_ITEM.get());
    }

    public static List<Item> creatureTabItems() {
        return List.of(EMBER_LORD_EGG.get(), VOID_LADY_EGG.get(), DROWNED_KING_EGG.get());
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
    }

    private WarlockContent() {}
}
