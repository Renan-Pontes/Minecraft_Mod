package com.renanpontes.isekaimahou.feature.espada;

import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.espada.aging.AgedEffect;
import com.renanpontes.isekaimahou.feature.espada.entity.Barragan;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaBolt;
import com.renanpontes.isekaimahou.feature.espada.entity.Grimmjow;
import com.renanpontes.isekaimahou.feature.espada.entity.Ulquiorra;
import com.renanpontes.isekaimahou.feature.espada.item.ArroganteShardItem;
import com.renanpontes.isekaimahou.feature.espada.item.MurcielagoWingItem;
import com.renanpontes.isekaimahou.feature.espada.item.PanteraClawItem;
import com.renanpontes.isekaimahou.feature.espada.item.ReishiRestorationItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo: os três Espada (ids reservados), o projétil de energia, o status Envelhecido, itens e ovos. */
public final class EspadaRegistries {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    // ---------------- Espada (ids reservados: o Las Noches invoca por estes ids) ----------------
    public static final Supplier<EntityType<Ulquiorra>> ULQUIORRA = ENTITIES.register("ulquiorra",
            () -> EntityType.Builder.of(Ulquiorra::new, MobCategory.MONSTER)
                    .sized(0.7F, 1.95F).eyeHeight(1.7F).clientTrackingRange(12).build("ulquiorra"));
    public static final Supplier<EntityType<Barragan>> BARRAGAN = ENTITIES.register("barragan",
            () -> EntityType.Builder.of(Barragan::new, MobCategory.MONSTER)
                    .sized(1.0F, 2.5F).eyeHeight(2.2F).clientTrackingRange(12).build("barragan"));
    public static final Supplier<EntityType<Grimmjow>> GRIMMJOW = ENTITIES.register("grimmjow",
            () -> EntityType.Builder.of(Grimmjow::new, MobCategory.MONSTER)
                    .sized(0.75F, 2.0F).eyeHeight(1.75F).clientTrackingRange(12).build("grimmjow"));

    /** Bala, lança de Luz de la Luna, dardo de Garra de la Pantera, Gran Caída arremessada. */
    public static final Supplier<EntityType<EspadaBolt>> BOLT = ENTITIES.register("espada_bolt",
            () -> EntityType.Builder.<EspadaBolt>of(EspadaBolt::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).clientTrackingRange(8).updateInterval(2).build("espada_bolt"));

    // ---------------- Status ----------------
    /** "Envelhecido" (Respira de Barragan). */
    public static final DeferredHolder<MobEffect, MobEffect> AGED = EFFECTS.register("aged", AgedEffect::new);

    // ---------------- Itens ----------------
    public static final DeferredItem<ReishiRestorationItem> REISHI_RESTORATION = ITEMS.register("reishi_restoration",
            () -> new ReishiRestorationItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final DeferredItem<MurcielagoWingItem> MURCIELAGO_WING = ITEMS.register("murcielago_wing",
            () -> new MurcielagoWingItem(new Item.Properties().stacksTo(1).durability(240).rarity(Rarity.EPIC)));
    public static final DeferredItem<ArroganteShardItem> ARROGANTE_SHARD = ITEMS.register("arrogante_shard",
            () -> new ArroganteShardItem(new Item.Properties().stacksTo(1).durability(120).rarity(Rarity.EPIC)));
    public static final DeferredItem<PanteraClawItem> PANTERA_CLAW = ITEMS.register("pantera_claw",
            () -> new PanteraClawItem(new Item.Properties().stacksTo(1).durability(300).rarity(Rarity.EPIC)));

    // ---------------- Ovos ----------------
    public static final DeferredItem<DeferredSpawnEggItem> ULQUIORRA_EGG = egg("ulquiorra", ULQUIORRA, 0xF2F2EC, 0x1F9E5A);
    public static final DeferredItem<DeferredSpawnEggItem> BARRAGAN_EGG = egg("barragan", BARRAGAN, 0xE8E0C8, 0x4A2A5E);
    public static final DeferredItem<DeferredSpawnEggItem> GRIMMJOW_EGG = egg("grimmjow", GRIMMJOW, 0xF0F0F0, 0x2FA8E8);

    private static DeferredItem<DeferredSpawnEggItem> egg(String id, Supplier<? extends EntityType<? extends Mob>> type, int back, int spots) {
        return ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(type, back, spots, new Item.Properties().rarity(Rarity.EPIC)));
    }

    public static List<DeferredItem<DeferredSpawnEggItem>> eggs() {
        return List.of(ULQUIORRA_EGG, BARRAGAN_EGG, GRIMMJOW_EGG);
    }

    public static List<DeferredItem<? extends Item>> items() {
        return List.of(REISHI_RESTORATION, MURCIELAGO_WING, ARROGANTE_SHARD, PANTERA_CLAW);
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(ULQUIORRA.get(), Ulquiorra.createAttributes().build());
            e.put(BARRAGAN.get(), Barragan.createAttributes().build());
            e.put(GRIMMJOW.get(), Grimmjow.createAttributes().build());
        });
    }

    private EspadaRegistries() {}
}
