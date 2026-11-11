package com.renanpontes.isekaimahou.feature.guild;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.guild.block.FactionBoardBlock;
import com.renanpontes.isekaimahou.feature.guild.block.GuildCounterBlock;
import com.renanpontes.isekaimahou.feature.guild.block.QuestBoardBlock;
import com.renanpontes.isekaimahou.feature.guild.entity.AdventurerEntity;
import com.renanpontes.isekaimahou.feature.guild.entity.GuildMasterEntity;
import com.renanpontes.isekaimahou.feature.guild.entity.GuildReceptionistEntity;
import com.renanpontes.isekaimahou.feature.guild.hunt.HuntBanner;
import com.renanpontes.isekaimahou.feature.guild.item.AdventurerCardItem;
import com.renanpontes.isekaimahou.feature.guild.item.FactionBoardItem;
import com.renanpontes.isekaimahou.feature.guild.item.GuildCardInfo;
import com.renanpontes.isekaimahou.feature.guild.item.GuildCoinItem;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registros do módulo Guilda: blocos, itens, NPCs, estandarte das Áreas de Caça, dados do jogador e o componente do cartão. */
public final class GuildContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IsekaiMahou.MODID);

    // ---------------- Dados do jogador ----------------
    public static final Supplier<AttachmentType<GuildData>> GUILD = ATTACHMENTS.register("guild",
            () -> AttachmentType.builder(() -> new GuildData()).serialize(GuildData.CODEC).copyOnDeath().build());

    // ---------------- Componente do cartão ----------------
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GuildCardInfo>> CARD = COMPONENTS.registerComponentType(
            "guild_card", b -> b.persistent(GuildCardInfo.CODEC).networkSynchronized(GuildCardInfo.STREAM_CODEC));

    // ---------------- Blocos ----------------
    public static final DeferredBlock<QuestBoardBlock> QUEST_BOARD = BLOCKS.registerBlock("quest_board", QuestBoardBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
    public static final DeferredBlock<GuildCounterBlock> GUILD_COUNTER = BLOCKS.registerBlock("guild_counter", GuildCounterBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.5F, 3.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());

    /** v0.8: Quadro de Facção (Guilda, Reino, Academia, Igreja, Submundo) — usado pelos moldes das cidades. */
    public static final DeferredBlock<FactionBoardBlock> FACTION_BOARD = BLOCKS.registerBlock("faction_board", FactionBoardBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava()
                    .lightLevel(s -> 3));

    public static final DeferredItem<BlockItem> QUEST_BOARD_ITEM = ITEMS.registerSimpleBlockItem("quest_board", QUEST_BOARD,
            new Item.Properties());
    public static final DeferredItem<BlockItem> GUILD_COUNTER_ITEM = ITEMS.registerSimpleBlockItem("guild_counter", GUILD_COUNTER,
            new Item.Properties());

    public static final DeferredItem<FactionBoardItem> FACTION_BOARD_ITEM = ITEMS.register("faction_board",
            () -> new FactionBoardItem(FACTION_BOARD.get(), new Item.Properties()));

    // ---------------- Itens ----------------
    public static final DeferredItem<AdventurerCardItem> ADVENTURER_CARD = ITEMS.register("adventurer_card",
            () -> new AdventurerCardItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<GuildCoinItem> GUILD_COIN = ITEMS.register("guild_coin",
            () -> new GuildCoinItem(new Item.Properties()));

    // ---------------- NPCs ----------------
    public static final Supplier<EntityType<GuildReceptionistEntity>> RECEPTIONIST = ENTITIES.register("guild_receptionist",
            () -> EntityType.Builder.<GuildReceptionistEntity>of(GuildReceptionistEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).build("guild_receptionist"));
    public static final Supplier<EntityType<GuildMasterEntity>> MASTER = ENTITIES.register("guild_master",
            () -> EntityType.Builder.<GuildMasterEntity>of(GuildMasterEntity::new, MobCategory.MISC)
                    .sized(0.7F, 2.0F).eyeHeight(1.8F).clientTrackingRange(10).build("guild_master"));
    public static final Supplier<EntityType<AdventurerEntity>> ADVENTURER = ENTITIES.register("adventurer",
            () -> EntityType.Builder.<AdventurerEntity>of(AdventurerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).build("adventurer"));

    /** v0.9: estandarte da Guilda fincado no centro de uma Área de Caça (temporário, não é salvo). */
    public static final Supplier<EntityType<HuntBanner>> HUNT_BANNER = ENTITIES.register("hunt_banner",
            () -> EntityType.Builder.<HuntBanner>of(HuntBanner::new, MobCategory.MISC)
                    .sized(0.9F, 4.4F).clientTrackingRange(16).updateInterval(20).fireImmune().build("hunt_banner"));

    public static final DeferredItem<DeferredSpawnEggItem> RECEPTIONIST_EGG = ITEMS.register("guild_receptionist_spawn_egg",
            () -> new DeferredSpawnEggItem(RECEPTIONIST, 0x2B3A67, 0xE8C77A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> MASTER_EGG = ITEMS.register("guild_master_spawn_egg",
            () -> new DeferredSpawnEggItem(MASTER, 0x5A3A22, 0xB03A2E, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ADVENTURER_EGG = ITEMS.register("adventurer_spawn_egg",
            () -> new DeferredSpawnEggItem(ADVENTURER, 0x3E6B35, 0xC9A66B, new Item.Properties()));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(GuildContent::attributes);
        modBus.addListener(GuildContent::creativeTabs);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(RECEPTIONIST.get(), GuildReceptionistEntity.createAttributes().build());
        e.put(MASTER.get(), GuildMasterEntity.createAttributes().build());
        e.put(ADVENTURER.get(), AdventurerEntity.createAttributes().build());
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            for (Item i : List.<Item>of(QUEST_BOARD_ITEM.get(), GUILD_COUNTER_ITEM.get(), ADVENTURER_CARD.get(), GUILD_COIN.get())) e.accept(i);
            for (GuildFaction f : GuildFaction.values()) e.accept(FactionBoardItem.of(FACTION_BOARD_ITEM.get(), f));
        } else if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(RECEPTIONIST_EGG.get());
            e.accept(MASTER_EGG.get());
            e.accept(ADVENTURER_EGG.get());
        }
    }

    private GuildContent() {}
}
