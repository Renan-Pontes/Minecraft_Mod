package com.renanpontes.isekaimahou.feature.creatures_e;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.DimensionalBolt;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.abyss.VoidCrawler;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.abyss.WatcherEye;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.abyss.YoungKraken;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.celestial.AngelSentinel;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.celestial.Cherub;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.celestial.CrystalGolem;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental.GreaterAirElemental;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental.GreaterEarthElemental;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental.GreaterFireElemental;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental.GreaterWaterElemental;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental.LesserPhoenix;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental.StormWyvern;
import com.renanpontes.isekaimahou.feature.creatures_e.item.AngelFeatherItem;
import com.renanpontes.isekaimahou.feature.creatures_e.item.CeMaterialItem;
import com.renanpontes.isekaimahou.feature.creatures_e.item.PhoenixFeatherItem;
import com.renanpontes.isekaimahou.feature.creatures_e.item.WatcherLensItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do modulo creatures_e: criaturas das dimensoes, o projetil delas, ovos e drops. */
public final class CeRegistries {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    /** Ovos e drops, na ordem em que aparecem na aba de criaturas. */
    public static final List<DeferredItem<? extends Item>> TAB_ITEMS = new ArrayList<>();

    // ---------------- Plano Elemental ----------------
    public static final Supplier<EntityType<GreaterFireElemental>> GREATER_FIRE_ELEMENTAL = ENTITIES.register("greater_fire_elemental",
            () -> EntityType.Builder.of(GreaterFireElemental::new, MobCategory.MONSTER).sized(1.2F, 2.9F).eyeHeight(2.45F)
                    .fireImmune().clientTrackingRange(10).build("greater_fire_elemental"));
    public static final Supplier<EntityType<GreaterWaterElemental>> GREATER_WATER_ELEMENTAL = ENTITIES.register("greater_water_elemental",
            () -> EntityType.Builder.of(GreaterWaterElemental::new, MobCategory.MONSTER).sized(1.2F, 2.8F).eyeHeight(2.4F)
                    .fireImmune().clientTrackingRange(10).build("greater_water_elemental"));
    public static final Supplier<EntityType<GreaterEarthElemental>> GREATER_EARTH_ELEMENTAL = ENTITIES.register("greater_earth_elemental",
            () -> EntityType.Builder.of(GreaterEarthElemental::new, MobCategory.MONSTER).sized(1.7F, 3.1F).eyeHeight(2.6F)
                    .clientTrackingRange(10).build("greater_earth_elemental"));
    public static final Supplier<EntityType<GreaterAirElemental>> GREATER_AIR_ELEMENTAL = ENTITIES.register("greater_air_elemental",
            () -> EntityType.Builder.of(GreaterAirElemental::new, MobCategory.MONSTER).sized(1.1F, 2.6F).eyeHeight(2.2F)
                    .clientTrackingRange(10).build("greater_air_elemental"));
    public static final Supplier<EntityType<LesserPhoenix>> LESSER_PHOENIX = ENTITIES.register("lesser_phoenix",
            () -> EntityType.Builder.of(LesserPhoenix::new, MobCategory.MONSTER).sized(1.0F, 1.0F).eyeHeight(0.75F)
                    .fireImmune().clientTrackingRange(10).build("lesser_phoenix"));
    public static final Supplier<EntityType<StormWyvern>> STORM_WYVERN = ENTITIES.register("storm_wyvern",
            () -> EntityType.Builder.of(StormWyvern::new, MobCategory.MONSTER).sized(2.2F, 1.7F).eyeHeight(1.35F)
                    .clientTrackingRange(12).build("storm_wyvern"));

    // ---------------- Abismo ----------------
    public static final Supplier<EntityType<VoidCrawler>> VOID_CRAWLER = ENTITIES.register("void_crawler",
            () -> EntityType.Builder.of(VoidCrawler::new, MobCategory.MONSTER).sized(1.5F, 0.9F).eyeHeight(0.6F)
                    .clientTrackingRange(8).build("void_crawler"));
    public static final Supplier<EntityType<WatcherEye>> WATCHER_EYE = ENTITIES.register("watcher_eye",
            () -> EntityType.Builder.of(WatcherEye::new, MobCategory.MONSTER).sized(1.2F, 1.2F).eyeHeight(0.6F)
                    .clientTrackingRange(10).build("watcher_eye"));
    public static final Supplier<EntityType<YoungKraken>> YOUNG_KRAKEN = ENTITIES.register("young_kraken",
            () -> EntityType.Builder.of(YoungKraken::new, MobCategory.MONSTER).sized(1.6F, 1.5F).eyeHeight(1.1F)
                    .clientTrackingRange(10).build("young_kraken"));

    // ---------------- Santuario Celestial ----------------
    public static final Supplier<EntityType<AngelSentinel>> ANGEL_SENTINEL = ENTITIES.register("angel_sentinel",
            () -> EntityType.Builder.of(AngelSentinel::new, MobCategory.MONSTER).sized(0.8F, 2.4F).eyeHeight(2.1F)
                    .clientTrackingRange(10).build("angel_sentinel"));
    public static final Supplier<EntityType<Cherub>> CHERUB = ENTITIES.register("cherub",
            () -> EntityType.Builder.of(Cherub::new, MobCategory.CREATURE).sized(0.6F, 0.9F).eyeHeight(0.6F)
                    .clientTrackingRange(8).build("cherub"));
    public static final Supplier<EntityType<CrystalGolem>> CRYSTAL_GOLEM = ENTITIES.register("crystal_golem",
            () -> EntityType.Builder.of(CrystalGolem::new, MobCategory.MONSTER).sized(1.5F, 2.8F).eyeHeight(2.4F)
                    .clientTrackingRange(10).build("crystal_golem"));

    // ---------------- Projetil das criaturas ----------------
    public static final Supplier<EntityType<DimensionalBolt>> DIMENSIONAL_BOLT = ENTITIES.register("dimensional_bolt",
            () -> EntityType.Builder.<DimensionalBolt>of(DimensionalBolt::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(8).updateInterval(2).build("dimensional_bolt"));

    // ---------------- Ovos ----------------
    static {
        egg("greater_fire_elemental", GREATER_FIRE_ELEMENTAL, 0x2B1D1A, 0xFF7A1A);
        egg("greater_water_elemental", GREATER_WATER_ELEMENTAL, 0x1E5FB8, 0xBFEFFF);
        egg("greater_earth_elemental", GREATER_EARTH_ELEMENTAL, 0x5E4A36, 0x7FBF4A);
        egg("greater_air_elemental", GREATER_AIR_ELEMENTAL, 0xDDEFF2, 0x6FE0C8);
        egg("lesser_phoenix", LESSER_PHOENIX, 0xD9361A, 0xFFD23F);
        egg("storm_wyvern", STORM_WYVERN, 0x34405E, 0x7FDBFF);
        egg("void_crawler", VOID_CRAWLER, 0x17121F, 0xA04BFF);
        egg("watcher_eye", WATCHER_EYE, 0x2A1838, 0xE8E0F0);
        egg("young_kraken", YOUNG_KRAKEN, 0x23325E, 0x6BE0D0);
        egg("angel_sentinel", ANGEL_SENTINEL, 0xF2EEE2, 0xE8B83A);
        egg("cherub", CHERUB, 0xFFE3D0, 0xFFE27A);
        egg("crystal_golem", CRYSTAL_GOLEM, 0xE8E4F4, 0xC07AF0);
    }

    // ---------------- Drops ----------------
    /** Material leve de guardiao: estilhaco cristalizado de um Elemental Maior. */
    public static final DeferredItem<Item> ELEMENTAL_SHARD = item("elemental_shard",
            () -> new CeMaterialItem(new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant(), true));
    public static final DeferredItem<Item> PHOENIX_FEATHER = item("phoenix_feather",
            () -> new PhoenixFeatherItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE).fireResistant()));
    public static final DeferredItem<Item> STORM_SCALE = item("storm_scale",
            () -> new CeMaterialItem(new Item.Properties().rarity(Rarity.UNCOMMON), false));
    public static final DeferredItem<Item> VOID_CHITIN = item("void_chitin",
            () -> new CeMaterialItem(new Item.Properties(), false));
    public static final DeferredItem<Item> WATCHER_LENS = item("watcher_lens",
            () -> new WatcherLensItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> KRAKEN_INK = item("kraken_ink",
            () -> new CeMaterialItem(new Item.Properties(), false));
    public static final DeferredItem<Item> ANGEL_FEATHER = item("angel_feather",
            () -> new AngelFeatherItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> LIVING_CRYSTAL = item("living_crystal",
            () -> new CeMaterialItem(new Item.Properties().rarity(Rarity.UNCOMMON), true));

    private static void egg(String id, Supplier<? extends EntityType<? extends Mob>> type, int bg, int spots) {
        TAB_ITEMS.add(ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(type, bg, spots, new Item.Properties())));
    }

    private static DeferredItem<Item> item(String id, Supplier<Item> factory) {
        DeferredItem<Item> it = ITEMS.register(id, factory);
        TAB_ITEMS.add(it);
        return it;
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }

    private CeRegistries() {}
}
