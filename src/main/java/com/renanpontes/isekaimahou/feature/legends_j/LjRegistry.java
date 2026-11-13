package com.renanpontes.isekaimahou.feature.legends_j;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_j.entity.MeteorRockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do legends_j: só a rocha do Tengai Shinsei (TEMPORÁRIA, {@code noSave}). */
public final class LjRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MeteorRockEntity>> METEOR_ROCK = ENTITIES.register("lj_meteor_rock",
            () -> EntityType.Builder.<MeteorRockEntity>of(MeteorRockEntity::new, MobCategory.MISC)
                    .sized(2.0F, 2.0F).noSave().fireImmune().clientTrackingRange(16).updateInterval(1).build("lj_meteor_rock"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    private LjRegistry() {}
}
