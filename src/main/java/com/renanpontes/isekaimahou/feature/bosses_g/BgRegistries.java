package com.renanpontes.isekaimahou.feature.bosses_g;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.FalseHydra;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.FalseHydraHead;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.Manticore;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.ManticoreSpike;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.MimicKing;
import com.renanpontes.isekaimahou.feature.bosses_g.entity.VaultMimic;
import com.renanpontes.isekaimahou.feature.bosses_g.item.FalseHydraHeartItem;
import com.renanpontes.isekaimahou.feature.bosses_g.item.GreedyCoinItem;
import com.renanpontes.isekaimahou.feature.bosses_g.item.ManticoreStingerItem;

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

/** Registros do módulo: os 3 chefes, as cabeças da Hidra, os mímicos do cofre, o espinho da Manticora, itens e ovos. */
public final class BgRegistries {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    // ---------------- Chefes ----------------
    public static final Supplier<EntityType<FalseHydra>> FALSE_HYDRA = ENTITIES.register("false_hydra",
            () -> EntityType.Builder.of(FalseHydra::new, MobCategory.MONSTER)
                    .sized(3.6F, 2.2F).eyeHeight(1.6F).clientTrackingRange(10).build("false_hydra"));
    public static final Supplier<EntityType<MimicKing>> MIMIC_KING = ENTITIES.register("mimic_king",
            () -> EntityType.Builder.of(MimicKing::new, MobCategory.MONSTER)
                    .sized(2.4F, 2.4F).eyeHeight(1.7F).clientTrackingRange(10).build("mimic_king"));
    public static final Supplier<EntityType<Manticore>> MANTICORE = ENTITIES.register("manticore",
            () -> EntityType.Builder.of(Manticore::new, MobCategory.MONSTER)
                    .sized(2.0F, 2.2F).eyeHeight(1.9F).clientTrackingRange(10).build("manticore"));

    // ---------------- Invocações e projéteis ----------------
    public static final Supplier<EntityType<FalseHydraHead>> FALSE_HYDRA_HEAD = ENTITIES.register("false_hydra_head",
            () -> EntityType.Builder.of(FalseHydraHead::new, MobCategory.MONSTER)
                    .sized(1.2F, 4.4F).eyeHeight(4.0F).clientTrackingRange(10).build("false_hydra_head"));
    public static final Supplier<EntityType<VaultMimic>> VAULT_MIMIC = ENTITIES.register("vault_mimic",
            () -> EntityType.Builder.of(VaultMimic::new, MobCategory.MONSTER)
                    .sized(0.9F, 0.9F).eyeHeight(0.6F).clientTrackingRange(8).build("vault_mimic"));
    public static final Supplier<EntityType<ManticoreSpike>> MANTICORE_SPIKE = ENTITIES.register("manticore_spike",
            () -> EntityType.Builder.<ManticoreSpike>of(ManticoreSpike::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(6).updateInterval(20).build("manticore_spike"));

    // ---------------- Itens ----------------
    public static final DeferredItem<FalseHydraHeartItem> FALSE_HYDRA_HEART = ITEMS.register("false_hydra_heart",
            () -> new FalseHydraHeartItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    public static final DeferredItem<GreedyCoinItem> GREEDY_COIN = ITEMS.register("greedy_coin",
            () -> new GreedyCoinItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final DeferredItem<ManticoreStingerItem> MANTICORE_STINGER = ITEMS.register("manticore_stinger",
            () -> new ManticoreStingerItem(new Item.Properties().rarity(Rarity.EPIC)));

    // ---------------- Ovos ----------------
    public static final DeferredItem<DeferredSpawnEggItem> FALSE_HYDRA_EGG = egg("false_hydra", FALSE_HYDRA, 0xD9CFC4, 0x8E6FD8);
    public static final DeferredItem<DeferredSpawnEggItem> FALSE_HYDRA_HEAD_EGG = egg("false_hydra_head", FALSE_HYDRA_HEAD, 0xC9BBB0, 0x4A2E5A);
    public static final DeferredItem<DeferredSpawnEggItem> MIMIC_KING_EGG = egg("mimic_king", MIMIC_KING, 0x7A2E1E, 0xF2C14A);
    public static final DeferredItem<DeferredSpawnEggItem> VAULT_MIMIC_EGG = egg("vault_mimic", VAULT_MIMIC, 0x8A5A2E, 0xD8507A);
    public static final DeferredItem<DeferredSpawnEggItem> MANTICORE_EGG = egg("manticore", MANTICORE, 0xC0862E, 0x5A1A12);

    private static DeferredItem<DeferredSpawnEggItem> egg(String id, Supplier<? extends EntityType<? extends Mob>> type, int back, int spots) {
        return ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(type, back, spots, new Item.Properties().rarity(Rarity.EPIC)));
    }

    public static List<DeferredItem<DeferredSpawnEggItem>> eggs() {
        return List.of(FALSE_HYDRA_EGG, FALSE_HYDRA_HEAD_EGG, MIMIC_KING_EGG, VAULT_MIMIC_EGG, MANTICORE_EGG);
    }

    public static List<DeferredItem<? extends Item>> items() {
        return List.of(FALSE_HYDRA_HEART, GREEDY_COIN, MANTICORE_STINGER);
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(FALSE_HYDRA.get(), FalseHydra.createAttributes().build());
            e.put(FALSE_HYDRA_HEAD.get(), FalseHydraHead.createAttributes().build());
            e.put(MIMIC_KING.get(), MimicKing.createAttributes().build());
            e.put(VAULT_MIMIC.get(), VaultMimic.createAttributes().build());
            e.put(MANTICORE.get(), Manticore.createAttributes().build());
        });
    }

    private BgRegistries() {}
}
