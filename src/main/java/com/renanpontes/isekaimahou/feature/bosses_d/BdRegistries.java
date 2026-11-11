package com.renanpontes.isekaimahou.feature.bosses_d;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialAvatar;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialSentinel;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo bosses_d: Avatar Elemental, Sentinela da Provação e seus ovos. */
public final class BdRegistries {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    public static final Supplier<EntityType<TrialAvatar>> TRIAL_AVATAR = ENTITIES.register("trial_avatar",
            () -> EntityType.Builder.of(TrialAvatar::new, MobCategory.MONSTER).sized(1.3F, 2.9F).eyeHeight(2.45F)
                    .clientTrackingRange(12).build("trial_avatar"));
    public static final Supplier<EntityType<TrialSentinel>> TRIAL_SENTINEL = ENTITIES.register("trial_sentinel",
            () -> EntityType.Builder.of(TrialSentinel::new, MobCategory.MONSTER).sized(1.2F, 2.3F).eyeHeight(1.95F)
                    .clientTrackingRange(10).build("trial_sentinel"));

    public static final DeferredItem<DeferredSpawnEggItem> TRIAL_AVATAR_EGG = ITEMS.register("trial_avatar_spawn_egg",
            () -> new DeferredSpawnEggItem(TRIAL_AVATAR, 0x2A2650, 0xC9B6FF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> TRIAL_SENTINEL_EGG = ITEMS.register("trial_sentinel_spawn_egg",
            () -> new DeferredSpawnEggItem(TRIAL_SENTINEL, 0xD9D6CF, 0x6B6660, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }

    private BdRegistries() {}
}
