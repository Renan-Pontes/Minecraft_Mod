package com.renanpontes.isekaimahou.feature.mounts;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.mounts.entity.Griffin;
import com.renanpontes.isekaimahou.feature.mounts.entity.Pegasus;
import com.renanpontes.isekaimahou.feature.mounts.entity.SkyRay;
import com.renanpontes.isekaimahou.feature.mounts.entity.Wyvern;
import com.renanpontes.isekaimahou.feature.mounts.item.MountWhistleItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo mounts: as quatro montarias voadoras, seus ovos e o Apito de Montaria. */
public final class MountsRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    // ---------------- Entidades ----------------
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<Griffin>> GRIFFIN = ENTITIES.register(Griffin.ID,
            () -> EntityType.Builder.<Griffin>of(Griffin::new, MobCategory.CREATURE)
                    .sized(1.5F, 1.8F).eyeHeight(1.75F).passengerAttachments(new Vec3(0.0, 1.38, 0.0))
                    .clientTrackingRange(12).build(Griffin.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<SkyRay>> SKY_RAY = ENTITIES.register(SkyRay.ID,
            () -> EntityType.Builder.<SkyRay>of(SkyRay::new, MobCategory.CREATURE)
                    .sized(1.9F, 0.6F).eyeHeight(0.4F).passengerAttachments(new Vec3(0.0, 0.58, 0.0))
                    .clientTrackingRange(12).build(SkyRay.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<Pegasus>> PEGASUS = ENTITIES.register(Pegasus.ID,
            () -> EntityType.Builder.<Pegasus>of(Pegasus::new, MobCategory.CREATURE)
                    .sized(1.3964844F, 1.7F).eyeHeight(1.52F).passengerAttachments(new Vec3(0.0, 1.38, 0.0))
                    .clientTrackingRange(12).build(Pegasus.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<Wyvern>> WYVERN = ENTITIES.register(Wyvern.ID,
            () -> EntityType.Builder.<Wyvern>of(Wyvern::new, MobCategory.MONSTER)
                    .sized(1.9F, 2.0F).eyeHeight(1.7F).passengerAttachments(new Vec3(0.0, 1.47, 0.0))
                    .clientTrackingRange(12).build(Wyvern.ID));

    // ---------------- Itens ----------------
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<DeferredSpawnEggItem> GRIFFIN_EGG = ITEMS.register("griffin_spawn_egg",
            () -> new DeferredSpawnEggItem(GRIFFIN, 0xB98A4E, 0xF2EEE6, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SKY_RAY_EGG = ITEMS.register("sky_ray_spawn_egg",
            () -> new DeferredSpawnEggItem(SKY_RAY, 0x1F5F8A, 0x7FF5E6, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> PEGASUS_EGG = ITEMS.register("pegasus_spawn_egg",
            () -> new DeferredSpawnEggItem(PEGASUS, 0xF4F4F8, 0xFFD86B, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> WYVERN_EGG = ITEMS.register("wyvern_spawn_egg",
            () -> new DeferredSpawnEggItem(WYVERN, 0x2A1A3A, 0xB06CFF, new Item.Properties()));

    /** Apito de Montaria: chama as montarias voadoras do dono que estiverem por perto. */
    public static final DeferredItem<MountWhistleItem> MOUNT_WHISTLE = ITEMS.register("mount_whistle",
            () -> new MountWhistleItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** Ordem na aba de criaturas. */
    public static List<DeferredItem<? extends Item>> tabItems() {
        return List.of(GRIFFIN_EGG, SKY_RAY_EGG, PEGASUS_EGG, WYVERN_EGG, MOUNT_WHISTLE);
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }

    private MountsRegistries() {}
}
