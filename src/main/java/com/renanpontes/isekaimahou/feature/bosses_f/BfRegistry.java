package com.renanpontes.isekaimahou.feature.bosses_f;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_f.entity.ColossusBoulder;
import com.renanpontes.isekaimahou.feature.bosses_f.entity.DesertBasilisk;
import com.renanpontes.isekaimahou.feature.bosses_f.entity.HydraVenomGlob;
import com.renanpontes.isekaimahou.feature.bosses_f.entity.RuinColossus;
import com.renanpontes.isekaimahou.feature.bosses_f.entity.SwampHydra;
import com.renanpontes.isekaimahou.feature.bosses_f.entity.WyvernKing;
import com.renanpontes.isekaimahou.feature.bosses_f.item.BossTrophyItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo: os 4 chefes do Overworld, os projéteis deles, os itens de chefe e os ovos. */
public final class BfRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    // ---------------- Chefes ----------------
    public static final Supplier<EntityType<SwampHydra>> SWAMP_HYDRA = ENTITIES.register("swamp_hydra",
            () -> EntityType.Builder.of(SwampHydra::new, MobCategory.MONSTER)
                    .sized(3.4F, 2.7F).eyeHeight(2.3F).clientTrackingRange(12).build("swamp_hydra"));
    public static final Supplier<EntityType<WyvernKing>> WYVERN_KING = ENTITIES.register("wyvern_king",
            () -> EntityType.Builder.of(WyvernKing::new, MobCategory.MONSTER)
                    .sized(2.6F, 2.4F).eyeHeight(2.0F).clientTrackingRange(14).build("wyvern_king"));
    public static final Supplier<EntityType<DesertBasilisk>> DESERT_BASILISK = ENTITIES.register("desert_basilisk",
            () -> EntityType.Builder.of(DesertBasilisk::new, MobCategory.MONSTER)
                    .sized(2.4F, 1.8F).eyeHeight(1.3F).clientTrackingRange(12).build("desert_basilisk"));
    public static final Supplier<EntityType<RuinColossus>> RUIN_COLOSSUS = ENTITIES.register("ruin_colossus",
            () -> EntityType.Builder.of(RuinColossus::new, MobCategory.MONSTER).fireImmune()
                    .sized(3.6F, 7.8F).eyeHeight(7.0F).clientTrackingRange(14).build("ruin_colossus"));

    // ---------------- Projéteis ----------------
    public static final Supplier<EntityType<HydraVenomGlob>> VENOM_GLOB = ENTITIES.register("swamp_hydra_venom_glob",
            () -> EntityType.Builder.<HydraVenomGlob>of(HydraVenomGlob::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(5).build("swamp_hydra_venom_glob"));
    public static final Supplier<EntityType<ColossusBoulder>> BOULDER = ENTITIES.register("ruin_colossus_boulder",
            () -> EntityType.Builder.<ColossusBoulder>of(ColossusBoulder::new, MobCategory.MISC)
                    .sized(1.2F, 1.2F).clientTrackingRange(10).updateInterval(5).build("ruin_colossus_boulder"));

    // ---------------- Itens de chefe (a Ascensão e outros módulos pedem por id) ----------------
    public static final DeferredItem<Item> HYDRA_FANG = material("swamp_hydra_fang", Rarity.UNCOMMON);
    public static final DeferredItem<Item> HYDRA_HEART = trophy("swamp_hydra_heart");
    public static final DeferredItem<Item> WYVERN_SCALE = material("wyvern_king_scale", Rarity.UNCOMMON);
    public static final DeferredItem<Item> WYVERN_HORN = trophy("wyvern_king_horn");
    public static final DeferredItem<Item> BASILISK_SCALE = material("desert_basilisk_scale", Rarity.UNCOMMON);
    public static final DeferredItem<Item> BASILISK_EYE = trophy("desert_basilisk_eye");
    public static final DeferredItem<Item> COLOSSUS_RUNE = material("ruin_colossus_rune", Rarity.UNCOMMON);
    public static final DeferredItem<Item> COLOSSUS_CORE = trophy("ruin_colossus_core");
    /** Só a aparência da bola de veneno em voo (não aparece no criativo). */
    public static final DeferredItem<Item> VENOM_GLOB_ITEM = ITEMS.register("swamp_hydra_venom_glob", () -> new Item(new Item.Properties()));

    // ---------------- Ovos ----------------
    public static final DeferredItem<DeferredSpawnEggItem> SWAMP_HYDRA_EGG = egg("swamp_hydra", SWAMP_HYDRA, 0x3E5B2E, 0x9BE04A);
    public static final DeferredItem<DeferredSpawnEggItem> WYVERN_KING_EGG = egg("wyvern_king", WYVERN_KING, 0x2E4A6E, 0x7FDBFF);
    public static final DeferredItem<DeferredSpawnEggItem> DESERT_BASILISK_EGG = egg("desert_basilisk", DESERT_BASILISK, 0xB88A4A, 0xD8FF5A);
    public static final DeferredItem<DeferredSpawnEggItem> RUIN_COLOSSUS_EGG = egg("ruin_colossus", RUIN_COLOSSUS, 0x8C8A80, 0x3FE0D0);

    private static DeferredItem<Item> material(String id, Rarity rarity) {
        return ITEMS.register(id, () -> new BossTrophyItem(new Item.Properties().rarity(rarity), false));
    }

    private static DeferredItem<Item> trophy(String id) {
        return ITEMS.register(id, () -> new BossTrophyItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(16).fireResistant(), true));
    }

    private static DeferredItem<DeferredSpawnEggItem> egg(String id, Supplier<? extends EntityType<? extends Mob>> type, int back, int spots) {
        return ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(type, back, spots, new Item.Properties().rarity(Rarity.EPIC)));
    }

    public static List<DeferredItem<DeferredSpawnEggItem>> eggs() {
        return List.of(SWAMP_HYDRA_EGG, WYVERN_KING_EGG, DESERT_BASILISK_EGG, RUIN_COLOSSUS_EGG);
    }

    public static List<DeferredItem<Item>> bossItems() {
        return List.of(HYDRA_HEART, HYDRA_FANG, WYVERN_HORN, WYVERN_SCALE, BASILISK_EYE, BASILISK_SCALE, COLOSSUS_CORE, COLOSSUS_RUNE);
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(SWAMP_HYDRA.get(), SwampHydra.createAttributes().build());
            e.put(WYVERN_KING.get(), WyvernKing.createAttributes().build());
            e.put(DESERT_BASILISK.get(), DesertBasilisk.createAttributes().build());
            e.put(RUIN_COLOSSUS.get(), RuinColossus.createAttributes().build());
        });
    }

    private BfRegistry() {}
}
