package com.renanpontes.isekaimahou.feature.fusions_c;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fusions_c.effect.PollenDrowsyEffect;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.BallLightningEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.FusionBeamEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.FusionShockwaveEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.IceCoffinEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.LavaBombEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.LavaGeyserEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.MiniVolcanoEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.PollenCloudEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.SacredSpringEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.SandCoffinEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.SandTsunamiEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.SupernovaEntity;
import com.renanpontes.isekaimahou.feature.fusions_c.entity.WorldRootEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registros das Misturas C: entidades de magia (todas TEMPORÁRIAS — noSave: somem ao descarregar o chunk) e o efeito
 * Sonolência. Nenhuma é criatura viva (não precisam de atributos nem ovo).
 */
public final class FcRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<LavaGeyserEntity>> LAVA_GEYSER =
            spell("lava_geyser_column", LavaGeyserEntity::new, 1.2F, 1.0F, 2);
    public static final DeferredHolder<EntityType<?>, EntityType<MiniVolcanoEntity>> MINI_VOLCANO =
            spell("mini_volcano", MiniVolcanoEntity::new, 3.0F, 3.0F, 4);
    public static final DeferredHolder<EntityType<?>, EntityType<LavaBombEntity>> LAVA_BOMB =
            spell("lava_bomb", LavaBombEntity::new, 0.6F, 0.6F, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<FusionShockwaveEntity>> FUSION_SHOCKWAVE =
            spell("fusion_shockwave", FusionShockwaveEntity::new, 1.0F, 0.5F, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<FusionBeamEntity>> FUSION_BEAM =
            spell("fusion_beam", FusionBeamEntity::new, 0.5F, 0.5F, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<BallLightningEntity>> BALL_LIGHTNING =
            spell("ball_lightning_orb", BallLightningEntity::new, 0.8F, 0.8F, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<IceCoffinEntity>> ICE_COFFIN =
            spell("ice_coffin_crystal", IceCoffinEntity::new, 1.0F, 2.0F, 2);
    public static final DeferredHolder<EntityType<?>, EntityType<WorldRootEntity>> WORLD_ROOT =
            spell("world_root", WorldRootEntity::new, 1.2F, 3.0F, 3);
    public static final DeferredHolder<EntityType<?>, EntityType<PollenCloudEntity>> POLLEN_CLOUD =
            spell("pollen_cloud", PollenCloudEntity::new, 1.0F, 1.0F, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<SandCoffinEntity>> SAND_COFFIN =
            spell("sand_coffin_shell", SandCoffinEntity::new, 1.0F, 2.0F, 2);
    public static final DeferredHolder<EntityType<?>, EntityType<SandTsunamiEntity>> SAND_TSUNAMI =
            spell("sand_tsunami_wave", SandTsunamiEntity::new, 2.0F, 2.0F, 1);
    public static final DeferredHolder<EntityType<?>, EntityType<SupernovaEntity>> SUPERNOVA =
            spell("supernova_star", SupernovaEntity::new, 1.0F, 1.0F, 2);
    public static final DeferredHolder<EntityType<?>, EntityType<SacredSpringEntity>> SACRED_SPRING =
            spell("sacred_spring_fountain", SacredSpringEntity::new, 1.6F, 2.4F, 10);

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    /** Sonolência (Tempestade de Pólen). */
    public static final DeferredHolder<MobEffect, MobEffect> POLLEN_DROWSY = EFFECTS.register("pollen_drowsy", PollenDrowsyEffect::new);

    private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> spell(String id, EntityType.EntityFactory<T> factory,
                                                                                          float w, float h, int updateInterval) {
        return ENTITIES.register(id, () -> EntityType.Builder.<T>of(factory, MobCategory.MISC)
                .sized(w, h).noSave().noSummon().fireImmune().clientTrackingRange(8).updateInterval(updateInterval)
                .build(id));
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
    }

    private FcRegistries() {}
}
