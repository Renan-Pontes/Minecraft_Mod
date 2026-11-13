package com.renanpontes.isekaimahou.feature.mounts;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.mounts.entity.AuroraStag;
import com.renanpontes.isekaimahou.feature.mounts.entity.Griffin;
import com.renanpontes.isekaimahou.feature.mounts.entity.LandDrake;
import com.renanpontes.isekaimahou.feature.mounts.entity.Nightmare;
import com.renanpontes.isekaimahou.feature.mounts.entity.Pegasus;
import com.renanpontes.isekaimahou.feature.mounts.entity.SkyRay;
import com.renanpontes.isekaimahou.feature.mounts.entity.Wyvern;
import com.renanpontes.isekaimahou.feature.mounts.item.MountReinsItem;
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

    // v1.0: montarias de chão (chegam pelas Rédeas; o Dragão Terrestre também vive solto)
    public static final DeferredHolder<EntityType<?>, EntityType<LandDrake>> LAND_DRAKE = ENTITIES.register(LandDrake.ID,
            () -> EntityType.Builder.<LandDrake>of(LandDrake::new, MobCategory.CREATURE)
                    .sized(1.2F, 1.75F).eyeHeight(1.6F).passengerAttachments(new Vec3(0.0, 1.42, 0.0))
                    .clientTrackingRange(10).build(LandDrake.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<Nightmare>> NIGHTMARE = ENTITIES.register(Nightmare.ID,
            () -> EntityType.Builder.<Nightmare>of(Nightmare::new, MobCategory.CREATURE)
                    .sized(1.3F, 1.7F).eyeHeight(1.55F).passengerAttachments(new Vec3(0.0, 1.38, 0.0))
                    .fireImmune().clientTrackingRange(10).build(Nightmare.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<AuroraStag>> AURORA_STAG = ENTITIES.register(AuroraStag.ID,
            () -> EntityType.Builder.<AuroraStag>of(AuroraStag::new, MobCategory.CREATURE)
                    .sized(1.2F, 1.7F).eyeHeight(1.55F).passengerAttachments(new Vec3(0.0, 1.38, 0.0))
                    .clientTrackingRange(10).build(AuroraStag.ID));

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

    public static final DeferredItem<DeferredSpawnEggItem> LAND_DRAKE_EGG = ITEMS.register("land_drake_spawn_egg",
            () -> new DeferredSpawnEggItem(LAND_DRAKE, 0x6E8B4A, 0xD9C27A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> NIGHTMARE_EGG = ITEMS.register("nightmare_spawn_egg",
            () -> new DeferredSpawnEggItem(NIGHTMARE, 0x1B1420, 0xFF7A1F, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> AURORA_STAG_EGG = ITEMS.register("aurora_stag_spawn_egg",
            () -> new DeferredSpawnEggItem(AURORA_STAG, 0xEFF4F8, 0x7FF5C8, new Item.Properties()));

    /** Rédeas: chamam a montaria já domada e selada (ids {@code <montaria>_reins}). */
    public static final DeferredItem<MountReinsItem> LAND_DRAKE_REINS = ITEMS.register("land_drake_reins",
            () -> new MountReinsItem(LAND_DRAKE, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<MountReinsItem> NIGHTMARE_REINS = ITEMS.register("nightmare_reins",
            () -> new MountReinsItem(NIGHTMARE, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<MountReinsItem> AURORA_STAG_REINS = ITEMS.register("aurora_stag_reins",
            () -> new MountReinsItem(AURORA_STAG, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** Ordem na aba de criaturas. */
    public static List<DeferredItem<? extends Item>> tabItems() {
        return List.of(GRIFFIN_EGG, SKY_RAY_EGG, PEGASUS_EGG, WYVERN_EGG, LAND_DRAKE_EGG, NIGHTMARE_EGG, AURORA_STAG_EGG,
                MOUNT_WHISTLE, LAND_DRAKE_REINS, NIGHTMARE_REINS, AURORA_STAG_REINS);
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }

    private MountsRegistries() {}
}
