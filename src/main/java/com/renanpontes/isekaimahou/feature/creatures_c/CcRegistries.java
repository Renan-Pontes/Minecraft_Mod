package com.renanpontes.isekaimahou.feature.creatures_c;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Basilisk;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.DireWolf;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Fairy;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Gargoyle;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Harpy;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Mimic;
import com.renanpontes.isekaimahou.feature.creatures_c.item.BasiliskEyeItem;
import com.renanpontes.isekaimahou.feature.creatures_c.item.CcLoreItem;
import com.renanpontes.isekaimahou.feature.creatures_c.item.FairyDustItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo creatures_c: as seis feras, seus ovos e os materiais que deixam cair. */
public final class CcRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    // ---------------- Entidades ----------------
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<DireWolf>> DIRE_WOLF = ENTITIES.register("dire_wolf",
            () -> EntityType.Builder.<DireWolf>of(DireWolf::new, MobCategory.CREATURE)
                    .sized(1.3F, 1.5F).eyeHeight(1.3F).passengerAttachments(1.42F).clientTrackingRange(10).build("dire_wolf"));
    public static final DeferredHolder<EntityType<?>, EntityType<Harpy>> HARPY = ENTITIES.register("harpy",
            () -> EntityType.Builder.<Harpy>of(Harpy::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F).eyeHeight(1.5F).clientTrackingRange(10).build("harpy"));
    public static final DeferredHolder<EntityType<?>, EntityType<Basilisk>> BASILISK = ENTITIES.register("basilisk",
            () -> EntityType.Builder.<Basilisk>of(Basilisk::new, MobCategory.MONSTER)
                    .sized(1.4F, 0.85F).eyeHeight(0.5F).clientTrackingRange(10).build("basilisk"));
    public static final DeferredHolder<EntityType<?>, EntityType<Mimic>> MIMIC = ENTITIES.register("mimic",
            () -> EntityType.Builder.<Mimic>of(Mimic::new, MobCategory.MONSTER)
                    .sized(0.875F, 0.9F).eyeHeight(0.6F).clientTrackingRange(8).build("mimic"));
    public static final DeferredHolder<EntityType<?>, EntityType<Gargoyle>> GARGOYLE = ENTITIES.register("gargoyle",
            () -> EntityType.Builder.<Gargoyle>of(Gargoyle::new, MobCategory.MONSTER)
                    .sized(0.9F, 1.9F).eyeHeight(1.65F).fireImmune().clientTrackingRange(10).build("gargoyle"));
    public static final DeferredHolder<EntityType<?>, EntityType<Fairy>> FAIRY = ENTITIES.register("fairy",
            () -> EntityType.Builder.<Fairy>of(Fairy::new, MobCategory.CREATURE)
                    .sized(0.45F, 0.8F).eyeHeight(0.65F).clientTrackingRange(8).build("fairy"));

    // ---------------- Itens ----------------
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<DeferredSpawnEggItem> DIRE_WOLF_EGG = ITEMS.register("dire_wolf_spawn_egg",
            () -> new DeferredSpawnEggItem(DIRE_WOLF, 0x3A3740, 0xA9A4B2, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> HARPY_EGG = ITEMS.register("harpy_spawn_egg",
            () -> new DeferredSpawnEggItem(HARPY, 0x7A5536, 0xE9C3A0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BASILISK_EGG = ITEMS.register("basilisk_spawn_egg",
            () -> new DeferredSpawnEggItem(BASILISK, 0x4E6B2E, 0xFFE14A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> MIMIC_EGG = ITEMS.register("mimic_spawn_egg",
            () -> new DeferredSpawnEggItem(MIMIC, 0xA47227, 0x8E2A35, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> GARGOYLE_EGG = ITEMS.register("gargoyle_spawn_egg",
            () -> new DeferredSpawnEggItem(GARGOYLE, 0x8A8A8E, 0xFF7A2E, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FAIRY_EGG = ITEMS.register("fairy_spawn_egg",
            () -> new DeferredSpawnEggItem(FAIRY, 0xFF9EC7, 0x96FFDC, new Item.Properties()));

    /** Pele do Lobo Atroz: vira couro. */
    public static final DeferredItem<CcLoreItem> DIRE_WOLF_PELT = ITEMS.register("dire_wolf_pelt",
            () -> new CcLoreItem(new Item.Properties()));
    /** Pena de Harpia: vira penas e é ingrediente de poção de queda lenta. */
    public static final DeferredItem<CcLoreItem> HARPY_FEATHER = ITEMS.register("harpy_feather",
            () -> new CcLoreItem(new Item.Properties()));
    /** Escama de Basilisco: ingrediente de poção de lentidão. */
    public static final DeferredItem<CcLoreItem> BASILISK_SCALE = ITEMS.register("basilisk_scale",
            () -> new CcLoreItem(new Item.Properties()));
    /** Olho de Basilisco: petrifica criaturas à frente (consumível). */
    public static final DeferredItem<BasiliskEyeItem> BASILISK_EYE = ITEMS.register("basilisk_eye",
            () -> new BasiliskEyeItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    /** Dente de Mímico: ingrediente de poção de sorte. */
    public static final DeferredItem<CcLoreItem> MIMIC_TOOTH = ITEMS.register("mimic_tooth",
            () -> new CcLoreItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    /** Fragmento de Gárgula: ingrediente de poção do mestre tartaruga. */
    public static final DeferredItem<CcLoreItem> GARGOYLE_FRAGMENT = ITEMS.register("gargoyle_fragment",
            () -> new CcLoreItem(new Item.Properties()));
    /** Pó de Fada: queda lenta + cura leve no ar; adubo mágico nos blocos. */
    public static final DeferredItem<FairyDustItem> FAIRY_DUST = ITEMS.register("fairy_dust",
            () -> new FairyDustItem(new Item.Properties().rarity(Rarity.UNCOMMON)));

    /** Ordem na aba de criaturas. */
    public static List<DeferredItem<? extends Item>> tabItems() {
        return List.of(DIRE_WOLF_EGG, HARPY_EGG, BASILISK_EGG, MIMIC_EGG, GARGOYLE_EGG, FAIRY_EGG,
                DIRE_WOLF_PELT, HARPY_FEATHER, BASILISK_SCALE, BASILISK_EYE, MIMIC_TOOTH, GARGOYLE_FRAGMENT, FAIRY_DUST);
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }

    private CcRegistries() {}
}
