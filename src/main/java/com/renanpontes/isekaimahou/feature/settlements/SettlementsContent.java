package com.renanpontes.isekaimahou.feature.settlements;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.settlements.block.SettlementHeartBlock;
import com.renanpontes.isekaimahou.feature.settlements.block.SettlementHeartBlockEntity;
import com.renanpontes.isekaimahou.feature.settlements.entity.BuilderNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.CityChampion;
import com.renanpontes.isekaimahou.feature.settlements.entity.GuardNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.MerchantNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.MilitiaNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.ResidentNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.SiteSign;
import com.renanpontes.isekaimahou.feature.settlements.item.FoundingCharterItem;
import com.renanpontes.isekaimahou.feature.settlements.item.OathSealItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo: coração da cidade do jogador, Carta de Fundação, Selo do Juramento, NPCs, Campeão e letreiro. */
public final class SettlementsContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IsekaiMahou.MODID);

    // ---------------- Coração ----------------
    /** Só nasce da Carta de Fundação; inquebrável em sobrevivência (o dono abandona a cidade pelo menu). */
    public static final DeferredBlock<SettlementHeartBlock> HEART = BLOCKS.registerBlock("settlement_heart", SettlementHeartBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(-1.0F, 3600000.0F).sound(SoundType.AMETHYST)
                    .noOcclusion().lightLevel(s -> 14).pushReaction(PushReaction.BLOCK).noLootTable());
    public static final Supplier<BlockEntityType<SettlementHeartBlockEntity>> HEART_BE = BLOCK_ENTITIES.register("settlement_heart",
            () -> BlockEntityType.Builder.of(SettlementHeartBlockEntity::new, HEART.get()).build(null));

    // ---------------- Itens ----------------
    public static final DeferredItem<FoundingCharterItem> FOUNDING_CHARTER = ITEMS.register("founding_charter",
            () -> new FoundingCharterItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<OathSealItem> OATH_SEAL = ITEMS.register("oath_seal",
            () -> new OathSealItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // ---------------- Entidades ----------------
    public static final Supplier<EntityType<BuilderNpc>> BUILDER = ENTITIES.register("settlement_builder",
            () -> EntityType.Builder.<BuilderNpc>of(BuilderNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.85F).eyeHeight(1.62F).clientTrackingRange(10).build("settlement_builder"));
    public static final Supplier<EntityType<ResidentNpc>> RESIDENT = ENTITIES.register("settlement_resident",
            () -> EntityType.Builder.<ResidentNpc>of(ResidentNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).build("settlement_resident"));
    public static final Supplier<EntityType<GuardNpc>> GUARD = ENTITIES.register("settlement_guard",
            () -> EntityType.Builder.<GuardNpc>of(GuardNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).eyeHeight(1.7F).clientTrackingRange(10).build("settlement_guard"));
    public static final Supplier<EntityType<MerchantNpc>> MERCHANT = ENTITIES.register("settlement_merchant",
            () -> EntityType.Builder.<MerchantNpc>of(MerchantNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).build("settlement_merchant"));
    public static final Supplier<EntityType<MilitiaNpc>> MILITIA = ENTITIES.register("settlement_militia",
            () -> EntityType.Builder.<MilitiaNpc>of(MilitiaNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).eyeHeight(1.7F).clientTrackingRange(10).build("settlement_militia"));
    public static final Supplier<EntityType<CityChampion>> CHAMPION = ENTITIES.register("city_champion",
            () -> EntityType.Builder.<CityChampion>of(CityChampion::new, MobCategory.MISC)
                    .sized(0.9F, 2.6F).eyeHeight(2.25F).clientTrackingRange(10).fireImmune().build("city_champion"));
    public static final Supplier<EntityType<SiteSign>> SITE_SIGN = ENTITIES.register("settlement_site_sign",
            () -> EntityType.Builder.<SiteSign>of(SiteSign::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(20).noSummon().build("settlement_site_sign"));

    public static final DeferredItem<DeferredSpawnEggItem> BUILDER_EGG = ITEMS.register("settlement_builder_spawn_egg",
            () -> new DeferredSpawnEggItem(BUILDER, 0xC9A27A, 0xE8B84A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> RESIDENT_EGG = ITEMS.register("settlement_resident_spawn_egg",
            () -> new DeferredSpawnEggItem(RESIDENT, 0xB98A5A, 0x6B8E3A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> GUARD_EGG = ITEMS.register("settlement_guard_spawn_egg",
            () -> new DeferredSpawnEggItem(GUARD, 0x9AA4B0, 0x4F7FE8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> MERCHANT_EGG = ITEMS.register("settlement_merchant_spawn_egg",
            () -> new DeferredSpawnEggItem(MERCHANT, 0x7A5636, 0xFFD86B, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> MILITIA_EGG = ITEMS.register("settlement_militia_spawn_egg",
            () -> new DeferredSpawnEggItem(MILITIA, 0x6A7686, 0xC9433A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> CHAMPION_EGG = ITEMS.register("city_champion_spawn_egg",
            () -> new DeferredSpawnEggItem(CHAMPION, 0xD8DEE6, 0xE8B84A, new Item.Properties()));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(SettlementsContent::attributes);
        modBus.addListener(SettlementsContent::creativeTabs);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(BUILDER.get(), BuilderNpc.createAttributes().build());
        e.put(RESIDENT.get(), ResidentNpc.createAttributes().build());
        e.put(GUARD.get(), GuardNpc.createAttributes().build());
        e.put(MERCHANT.get(), MerchantNpc.createAttributes().build());
        e.put(MILITIA.get(), MilitiaNpc.createAttributes().build());
        e.put(CHAMPION.get(), CityChampion.createAttributes().build());
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            e.accept(FOUNDING_CHARTER.get());
            e.accept(OATH_SEAL.get());
        } else if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(BUILDER_EGG.get());
            e.accept(RESIDENT_EGG.get());
            e.accept(GUARD_EGG.get());
            e.accept(MERCHANT_EGG.get());
            e.accept(MILITIA_EGG.get());
            e.accept(CHAMPION_EGG.get());
        }
    }

    private SettlementsContent() {}
}
