package com.renanpontes.isekaimahou.feature.bosses_a;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.GolemBoulder;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.GolemPillar;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.GolemSlab;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.GuardianShockwave;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.IgnisEntity;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.IgnisLavaPool;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.IgnisMeteor;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.PrimordialGolemEntity;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.TideBubble;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.TideGuardianEntity;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.TideWhirlpool;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.ZephyrCurtain;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.ZephyrEntity;
import com.renanpontes.isekaimahou.feature.bosses_a.entity.ZephyrTornado;

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

/** Registros do módulo: os 4 Guardiões Elementais, as entidades dos ataques e os ovos. */
public final class BossesARegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    // ---------------- Chefes ----------------
    public static final Supplier<EntityType<IgnisEntity>> IGNIS = ENTITIES.register("ignis",
            () -> EntityType.Builder.of(IgnisEntity::new, MobCategory.MONSTER).fireImmune()
                    .sized(2.2F, 4.6F).eyeHeight(4.1F).clientTrackingRange(12).build("ignis"));
    public static final Supplier<EntityType<TideGuardianEntity>> TIDE_GUARDIAN = ENTITIES.register("tide_guardian",
            () -> EntityType.Builder.of(TideGuardianEntity::new, MobCategory.MONSTER)
                    .sized(2.0F, 3.2F).eyeHeight(2.6F).clientTrackingRange(12).build("tide_guardian"));
    public static final Supplier<EntityType<PrimordialGolemEntity>> PRIMORDIAL_GOLEM = ENTITIES.register("primordial_golem",
            () -> EntityType.Builder.of(PrimordialGolemEntity::new, MobCategory.MONSTER)
                    .sized(3.4F, 5.4F).eyeHeight(4.3F).clientTrackingRange(12).build("primordial_golem"));
    public static final Supplier<EntityType<ZephyrEntity>> ZEPHYR = ENTITIES.register("zephyr",
            () -> EntityType.Builder.of(ZephyrEntity::new, MobCategory.MONSTER)
                    .sized(1.8F, 3.2F).eyeHeight(2.7F).clientTrackingRange(12).build("zephyr"));

    // ---------------- Entidades dos ataques (temporárias, não são salvas) ----------------
    public static final Supplier<EntityType<GuardianShockwave>> SHOCKWAVE = ENTITIES.register("guardian_shockwave",
            () -> EntityType.Builder.<GuardianShockwave>of(GuardianShockwave::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(20).build("guardian_shockwave"));
    public static final Supplier<EntityType<IgnisMeteor>> METEOR = ENTITIES.register("ignis_meteor",
            () -> EntityType.Builder.<IgnisMeteor>of(IgnisMeteor::new, MobCategory.MISC).fireImmune()
                    .sized(1.2F, 1.2F).clientTrackingRange(10).updateInterval(2).build("ignis_meteor"));
    public static final Supplier<EntityType<IgnisLavaPool>> LAVA_POOL = ENTITIES.register("ignis_lava_pool",
            () -> EntityType.Builder.<IgnisLavaPool>of(IgnisLavaPool::new, MobCategory.MISC).fireImmune()
                    .sized(5.5F, 0.4F).clientTrackingRange(10).updateInterval(20).build("ignis_lava_pool"));
    public static final Supplier<EntityType<GolemBoulder>> BOULDER = ENTITIES.register("golem_boulder",
            () -> EntityType.Builder.<GolemBoulder>of(GolemBoulder::new, MobCategory.MISC)
                    .sized(1.2F, 1.2F).clientTrackingRange(10).updateInterval(2).build("golem_boulder"));
    public static final Supplier<EntityType<ZephyrTornado>> TORNADO = ENTITIES.register("zephyr_tornado",
            () -> EntityType.Builder.<ZephyrTornado>of(ZephyrTornado::new, MobCategory.MISC)
                    .sized(3.0F, 4.2F).clientTrackingRange(10).updateInterval(2).build("zephyr_tornado"));
    public static final Supplier<EntityType<TideWhirlpool>> WHIRLPOOL = ENTITIES.register("tide_whirlpool",
            () -> EntityType.Builder.<TideWhirlpool>of(TideWhirlpool::new, MobCategory.MISC)
                    .sized(12.0F, 0.5F).clientTrackingRange(10).updateInterval(20).build("tide_whirlpool"));
    public static final Supplier<EntityType<TideBubble>> TIDE_BUBBLE = ENTITIES.register("tide_bubble",
            () -> EntityType.Builder.<TideBubble>of(TideBubble::new, MobCategory.MISC)
                    .sized(2.0F, 2.4F).clientTrackingRange(10).updateInterval(2).build("tide_bubble"));
    public static final Supplier<EntityType<GolemPillar>> GOLEM_PILLAR = ENTITIES.register("golem_pillar",
            () -> EntityType.Builder.<GolemPillar>of(GolemPillar::new, MobCategory.MISC)
                    .sized(1.2F, 3.0F).clientTrackingRange(10).updateInterval(40).build("golem_pillar"));
    public static final Supplier<EntityType<GolemSlab>> GOLEM_SLAB = ENTITIES.register("golem_slab",
            () -> EntityType.Builder.<GolemSlab>of(GolemSlab::new, MobCategory.MISC)
                    .sized(2.0F, 1.0F).clientTrackingRange(10).updateInterval(1).build("golem_slab"));
    public static final Supplier<EntityType<ZephyrCurtain>> ZEPHYR_CURTAIN = ENTITIES.register("zephyr_curtain",
            () -> EntityType.Builder.<ZephyrCurtain>of(ZephyrCurtain::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(10).updateInterval(40).build("zephyr_curtain"));

    // ---------------- Ovos ----------------
    public static final DeferredItem<DeferredSpawnEggItem> IGNIS_EGG = ITEMS.register("ignis_spawn_egg",
            () -> new DeferredSpawnEggItem(IGNIS, 0x2B2422, 0xFF7A1A, new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredItem<DeferredSpawnEggItem> TIDE_GUARDIAN_EGG = ITEMS.register("tide_guardian_spawn_egg",
            () -> new DeferredSpawnEggItem(TIDE_GUARDIAN, 0x1F7A8C, 0x7FF5FF, new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredItem<DeferredSpawnEggItem> PRIMORDIAL_GOLEM_EGG = ITEMS.register("primordial_golem_spawn_egg",
            () -> new DeferredSpawnEggItem(PRIMORDIAL_GOLEM, 0x5E5A55, 0x8CFF6B, new Item.Properties().rarity(Rarity.EPIC)));
    public static final DeferredItem<DeferredSpawnEggItem> ZEPHYR_EGG = ITEMS.register("zephyr_spawn_egg",
            () -> new DeferredSpawnEggItem(ZEPHYR, 0xE8F6FF, 0x5FD9B0, new Item.Properties().rarity(Rarity.EPIC)));

    public static List<DeferredItem<DeferredSpawnEggItem>> eggs() {
        return List.of(IGNIS_EGG, TIDE_GUARDIAN_EGG, PRIMORDIAL_GOLEM_EGG, ZEPHYR_EGG);
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(IGNIS.get(), IgnisEntity.createAttributes().build());
            e.put(TIDE_GUARDIAN.get(), TideGuardianEntity.createAttributes().build());
            e.put(PRIMORDIAL_GOLEM.get(), PrimordialGolemEntity.createAttributes().build());
            e.put(ZEPHYR.get(), ZephyrEntity.createAttributes().build());
        });
    }

    private BossesARegistry() {}
}
