package com.renanpontes.isekaimahou.feature.cities;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.cities.block.CityHeartBlock;
import com.renanpontes.isekaimahou.feature.cities.block.CityHeartBlockEntity;
import com.renanpontes.isekaimahou.feature.cities.effect.AuroraBlessingEffect;
import com.renanpontes.isekaimahou.feature.cities.effect.WantedEffect;
import com.renanpontes.isekaimahou.feature.cities.entity.Citizen;
import com.renanpontes.isekaimahou.feature.cities.entity.CityGuard;
import com.renanpontes.isekaimahou.feature.cities.entity.CityLord;
import com.renanpontes.isekaimahou.feature.cities.entity.CityMerchant;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
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
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo Cidades: coração da cidade, NPCs, ovos e efeitos (Procurado, Bênção da Aurora). */
public final class CitiesContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    // ---------------- Coração da cidade ----------------
    /** Pedestal com cristal azul na praça: inquebrável em sobrevivência (é o que mantém a cidade viva). */
    public static final DeferredBlock<CityHeartBlock> CITY_HEART = BLOCKS.registerBlock("city_heart", CityHeartBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(-1.0F, 3600000.0F)
                    .sound(SoundType.AMETHYST).noOcclusion().lightLevel(s -> 13).pushReaction(PushReaction.BLOCK)
                    .noLootTable());
    public static final DeferredItem<BlockItem> CITY_HEART_ITEM = ITEMS.registerSimpleBlockItem("city_heart", CITY_HEART,
            new Item.Properties().rarity(Rarity.EPIC));
    public static final Supplier<BlockEntityType<CityHeartBlockEntity>> CITY_HEART_BE = BLOCK_ENTITIES.register("city_heart",
            () -> BlockEntityType.Builder.of(CityHeartBlockEntity::new, CITY_HEART.get()).build(null));

    // ---------------- NPCs ----------------
    public static final Supplier<EntityType<CityGuard>> CITY_GUARD = ENTITIES.register("city_guard",
            () -> EntityType.Builder.<CityGuard>of(CityGuard::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).eyeHeight(1.7F).clientTrackingRange(10).build("city_guard"));
    public static final Supplier<EntityType<Citizen>> CITIZEN = ENTITIES.register("citizen",
            () -> EntityType.Builder.<Citizen>of(Citizen::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).build("citizen"));
    public static final Supplier<EntityType<CityMerchant>> CITY_MERCHANT = ENTITIES.register("city_merchant",
            () -> EntityType.Builder.<CityMerchant>of(CityMerchant::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).build("city_merchant"));
    public static final Supplier<EntityType<CityLord>> CITY_LORD = ENTITIES.register("city_lord",
            () -> EntityType.Builder.<CityLord>of(CityLord::new, MobCategory.MISC)
                    .sized(0.6F, 1.9F).eyeHeight(1.68F).clientTrackingRange(10).build("city_lord"));

    public static final DeferredItem<DeferredSpawnEggItem> CITY_GUARD_EGG = ITEMS.register("city_guard_spawn_egg",
            () -> new DeferredSpawnEggItem(CITY_GUARD, 0x8E99A8, 0x3F6FD8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> CITIZEN_EGG = ITEMS.register("citizen_spawn_egg",
            () -> new DeferredSpawnEggItem(CITIZEN, 0xC9A27A, 0x6B8E3A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> CITY_MERCHANT_EGG = ITEMS.register("city_merchant_spawn_egg",
            () -> new DeferredSpawnEggItem(CITY_MERCHANT, 0x7A5636, 0xE8B84A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> CITY_LORD_EGG = ITEMS.register("city_lord_spawn_egg",
            () -> new DeferredSpawnEggItem(CITY_LORD, 0x6E1622, 0xFFD86B, new Item.Properties()));

    // ---------------- Efeitos ----------------
    public static final DeferredHolder<MobEffect, WantedEffect> WANTED = EFFECTS.register("wanted", WantedEffect::new);
    public static final DeferredHolder<MobEffect, AuroraBlessingEffect> AURORA_BLESSING =
            EFFECTS.register("aurora_blessing", AuroraBlessingEffect::new);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener(CitiesContent::attributes);
        modBus.addListener(CitiesContent::creativeTabs);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(CITY_GUARD.get(), CityGuard.createAttributes().build());
        e.put(CITIZEN.get(), Citizen.createAttributes().build());
        e.put(CITY_MERCHANT.get(), CityMerchant.createAttributes().build());
        e.put(CITY_LORD.get(), CityLord.createAttributes().build());
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            e.accept(CITY_HEART_ITEM.get());
        } else if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(CITY_GUARD_EGG.get());
            e.accept(CITIZEN_EGG.get());
            e.accept(CITY_MERCHANT_EGG.get());
            e.accept(CITY_LORD_EGG.get());
        }
    }

    private CitiesContent() {}
}
