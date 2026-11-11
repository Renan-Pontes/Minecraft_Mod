package com.renanpontes.isekaimahou.core.status;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do núcleo A1 (os efeitos ficam no CoreRegistries). */
public final class StatusRegistries {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);

    /** Escombro visual da destruição de terreno. */
    public static final Supplier<EntityType<DebrisEntity>> DEBRIS = ENTITIES.register("destruction_debris",
            () -> EntityType.Builder.<DebrisEntity>of(DebrisEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).noSave().noSummon().fireImmune()
                    .clientTrackingRange(8).updateInterval(3).setShouldReceiveVelocityUpdates(true)
                    .build("destruction_debris"));

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    private StatusRegistries() {}
}
