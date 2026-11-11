package com.renanpontes.isekaimahou.feature.legends_b;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_b.entity.ConvergeSphereEntity;
import com.renanpontes.isekaimahou.feature.legends_b.entity.SkyLancesEntity;
import com.renanpontes.isekaimahou.feature.legends_b.entity.SpiralOrbEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entidades das formas lendárias do módulo legends_b. */
public final class LegendsBRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);

    /** Convergência: orbes que se fundem e a esfera que atravessa tudo. Rastreada de longe (a esfera viaja 40 blocos). */
    public static final DeferredHolder<EntityType<?>, EntityType<ConvergeSphereEntity>> CONVERGE_SPHERE = ENTITIES.register("converge_sphere",
            () -> EntityType.Builder.<ConvergeSphereEntity>of(ConvergeSphereEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().clientTrackingRange(10).updateInterval(5).build("converge_sphere"));

    /** Lanças do Céu: a formação de lentes no céu sobre a área mirada. */
    public static final DeferredHolder<EntityType<?>, EntityType<SkyLancesEntity>> SKY_LANCES = ENTITIES.register("sky_lances_array",
            () -> EntityType.Builder.<SkyLancesEntity>of(SkyLancesEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().clientTrackingRange(10).updateInterval(20).build("sky_lances_array"));

    /** Esfera Espiral: presa à mão de quem conjura (não é salva: some ao descarregar). */
    public static final DeferredHolder<EntityType<?>, EntityType<SpiralOrbEntity>> SPIRAL_ORB = ENTITIES.register("spiral_sphere_orb",
            () -> EntityType.Builder.<SpiralOrbEntity>of(SpiralOrbEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSave().clientTrackingRange(8).updateInterval(2).build("spiral_sphere_orb"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    private LegendsBRegistry() {}
}
