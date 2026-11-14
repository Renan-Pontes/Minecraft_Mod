package com.renanpontes.isekaimahou.feature.dark_self;

import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.dark_self.entity.DarkSelfEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo "dark_self": só a entidade da Sombra. */
public final class DarkSelfRegistry {
    static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);

    public static final Supplier<EntityType<DarkSelfEntity>> DARK_SELF = ENTITIES.register("dark_self",
            () -> EntityType.Builder.<DarkSelfEntity>of(DarkSelfEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10).build("dark_self"));

    static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    private DarkSelfRegistry() {}
}
