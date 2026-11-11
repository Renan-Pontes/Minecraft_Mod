package com.renanpontes.isekaimahou.feature.bosses_c;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_c.entity.ArchmageIllusion;
import com.renanpontes.isekaimahou.feature.bosses_c.entity.ClassBossBolt;
import com.renanpontes.isekaimahou.feature.bosses_c.entity.CorruptedEnt;
import com.renanpontes.isekaimahou.feature.bosses_c.entity.CorruptedWitch;
import com.renanpontes.isekaimahou.feature.bosses_c.entity.CursedBottle;
import com.renanpontes.isekaimahou.feature.bosses_c.entity.EntRoot;
import com.renanpontes.isekaimahou.feature.bosses_c.entity.HexCat;
import com.renanpontes.isekaimahou.feature.bosses_c.entity.ImprisonedPatron;
import com.renanpontes.isekaimahou.feature.bosses_c.entity.Lich;
import com.renanpontes.isekaimahou.feature.bosses_c.entity.LichPhylactery;
import com.renanpontes.isekaimahou.feature.bosses_c.entity.RenegadeArchmage;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo: os 5 minichefes de classe, as invocações/ataques deles e os ovos. */
public final class BossesCRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    // ---------------- Chefes ----------------
    public static final Supplier<EntityType<RenegadeArchmage>> RENEGADE_ARCHMAGE = ENTITIES.register("renegade_archmage",
            () -> EntityType.Builder.of(RenegadeArchmage::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.5F).eyeHeight(2.1F).clientTrackingRange(10).build("renegade_archmage"));
    public static final Supplier<EntityType<CorruptedWitch>> CORRUPTED_WITCH = ENTITIES.register("corrupted_witch",
            () -> EntityType.Builder.of(CorruptedWitch::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.3F).eyeHeight(1.75F).clientTrackingRange(10).build("corrupted_witch"));
    public static final Supplier<EntityType<ImprisonedPatron>> IMPRISONED_PATRON = ENTITIES.register("imprisoned_patron",
            () -> EntityType.Builder.of(ImprisonedPatron::new, MobCategory.MONSTER).fireImmune()
                    .sized(1.6F, 3.3F).eyeHeight(2.6F).clientTrackingRange(10).build("imprisoned_patron"));
    public static final Supplier<EntityType<CorruptedEnt>> CORRUPTED_ENT = ENTITIES.register("corrupted_ent",
            () -> EntityType.Builder.of(CorruptedEnt::new, MobCategory.MONSTER)
                    .sized(2.0F, 4.4F).eyeHeight(3.1F).clientTrackingRange(10).build("corrupted_ent"));
    public static final Supplier<EntityType<Lich>> LICH = ENTITIES.register("lich",
            () -> EntityType.Builder.of(Lich::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.6F).eyeHeight(2.2F).clientTrackingRange(10).build("lich"));

    // ---------------- Invocações e ataques ----------------
    public static final Supplier<EntityType<ArchmageIllusion>> ARCHMAGE_ILLUSION = ENTITIES.register("archmage_illusion",
            () -> EntityType.Builder.of(ArchmageIllusion::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.5F).eyeHeight(2.1F).clientTrackingRange(10).build("archmage_illusion"));
    public static final Supplier<EntityType<HexCat>> HEX_CAT = ENTITIES.register("hex_cat",
            () -> EntityType.Builder.of(HexCat::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.7F).clientTrackingRange(8).build("hex_cat"));
    public static final Supplier<EntityType<LichPhylactery>> LICH_PHYLACTERY = ENTITIES.register("lich_phylactery",
            () -> EntityType.Builder.of(LichPhylactery::new, MobCategory.MISC).fireImmune()
                    .sized(0.8F, 1.2F).clientTrackingRange(10).build("lich_phylactery"));
    public static final Supplier<EntityType<EntRoot>> ENT_ROOT = ENTITIES.register("corrupted_ent_root",
            () -> EntityType.Builder.<EntRoot>of(EntRoot::new, MobCategory.MISC)
                    .sized(1.0F, 1.4F).clientTrackingRange(8).updateInterval(20).build("corrupted_ent_root"));
    public static final Supplier<EntityType<ClassBossBolt>> BOLT = ENTITIES.register("class_boss_bolt",
            () -> EntityType.Builder.<ClassBossBolt>of(ClassBossBolt::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(2).build("class_boss_bolt"));
    public static final Supplier<EntityType<CursedBottle>> CURSED_BOTTLE = ENTITIES.register("cursed_bottle",
            () -> EntityType.Builder.<CursedBottle>of(CursedBottle::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(10).build("cursed_bottle"));

    // ---------------- Ovos ----------------
    public static final DeferredItem<DeferredSpawnEggItem> RENEGADE_ARCHMAGE_EGG = egg("renegade_archmage", RENEGADE_ARCHMAGE, 0x2A1856, 0xC9A2FF);
    public static final DeferredItem<DeferredSpawnEggItem> CORRUPTED_WITCH_EGG = egg("corrupted_witch", CORRUPTED_WITCH, 0x3B2A4A, 0x7CFF4A);
    public static final DeferredItem<DeferredSpawnEggItem> IMPRISONED_PATRON_EGG = egg("imprisoned_patron", IMPRISONED_PATRON, 0x7A1622, 0x5E5E66);
    public static final DeferredItem<DeferredSpawnEggItem> CORRUPTED_ENT_EGG = egg("corrupted_ent", CORRUPTED_ENT, 0x4A3526, 0xC66BFF);
    public static final DeferredItem<DeferredSpawnEggItem> LICH_EGG = egg("lich", LICH, 0x2A2A3A, 0x5FD8E8);

    private static DeferredItem<DeferredSpawnEggItem> egg(String id, Supplier<? extends EntityType<? extends net.minecraft.world.entity.Mob>> type,
                                                          int back, int spots) {
        return ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(type, back, spots, new Item.Properties().rarity(Rarity.EPIC)));
    }

    public static List<DeferredItem<DeferredSpawnEggItem>> eggs() {
        return List.of(RENEGADE_ARCHMAGE_EGG, CORRUPTED_WITCH_EGG, IMPRISONED_PATRON_EGG, CORRUPTED_ENT_EGG, LICH_EGG);
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(RENEGADE_ARCHMAGE.get(), RenegadeArchmage.createAttributes().build());
            e.put(CORRUPTED_WITCH.get(), CorruptedWitch.createAttributes().build());
            e.put(IMPRISONED_PATRON.get(), ImprisonedPatron.createAttributes().build());
            e.put(CORRUPTED_ENT.get(), CorruptedEnt.createAttributes().build());
            e.put(LICH.get(), Lich.createAttributes().build());
            e.put(ARCHMAGE_ILLUSION.get(), ArchmageIllusion.createAttributes().build());
            e.put(HEX_CAT.get(), HexCat.createAttributes().build());
            e.put(LICH_PHYLACTERY.get(), LichPhylactery.createAttributes().build());
        });
    }

    private BossesCRegistry() {}
}
