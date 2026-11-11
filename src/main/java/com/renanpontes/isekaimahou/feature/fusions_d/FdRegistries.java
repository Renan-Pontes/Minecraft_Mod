package com.renanpontes.isekaimahou.feature.fusions_d;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fusions_d.effect.SoulBurnEffect;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.AbyssalMawEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.BlackHoleEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.CrystalLotusEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.CrystalSpikeEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.DimensionCutEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.GravityFieldEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.HellfirePillarEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.KrakenTentacleEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.PlanetCoreEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.PrismBeamEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.RubbleEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.StarfallStarEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.TwilightPillarEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.VoidTearEntity;
import com.renanpontes.isekaimahou.feature.fusions_d.entity.YinYangEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registros das Misturas D (Cristal, Astral, Chama Infernal, Abismo, Gravidade, Vazio, Crepúsculo — v0.8).
 * Todas as entidades são de magia: temporárias, nunca salvas (somem ao descarregar o chunk) e sem ovo.
 */
public final class FdRegistries {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    /** Bloco arrancado do terreno voando (Buraco Negro, Esmagamento Planetário). Só visual. */
    public static final DeferredHolder<EntityType<?>, EntityType<RubbleEntity>> RUBBLE =
            reg("fd_rubble", RubbleEntity::new, 0.8F, 0.8F, 8, 20);
    public static final DeferredHolder<EntityType<?>, EntityType<BlackHoleEntity>> BLACK_HOLE =
            reg("fd_black_hole", BlackHoleEntity::new, 1.5F, 1.5F, 12, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<PlanetCoreEntity>> PLANET_CORE =
            reg("fd_planet_core", PlanetCoreEntity::new, 1.0F, 1.0F, 12, 2);
    public static final DeferredHolder<EntityType<?>, EntityType<CrystalLotusEntity>> CRYSTAL_LOTUS =
            reg("fd_crystal_lotus", CrystalLotusEntity::new, 1.6F, 0.8F, 10, 20);
    public static final DeferredHolder<EntityType<?>, EntityType<CrystalSpikeEntity>> CRYSTAL_SPIKE =
            reg("fd_crystal_spike", CrystalSpikeEntity::new, 0.3F, 0.3F, 6, 20);
    public static final DeferredHolder<EntityType<?>, EntityType<PrismBeamEntity>> PRISM_BEAM =
            reg("fd_prism_beam", PrismBeamEntity::new, 0.6F, 0.6F, 10, 20);
    public static final DeferredHolder<EntityType<?>, EntityType<StarfallStarEntity>> STAR =
            reg("fd_star", StarfallStarEntity::new, 0.6F, 0.6F, 12, 20);
    public static final DeferredHolder<EntityType<?>, EntityType<HellfirePillarEntity>> HELLFIRE_PILLAR =
            reg("fd_hellfire_pillar", HellfirePillarEntity::new, 2.0F, 4.0F, 12, 20);
    public static final DeferredHolder<EntityType<?>, EntityType<KrakenTentacleEntity>> KRAKEN_TENTACLE =
            reg("fd_kraken_tentacle", KrakenTentacleEntity::new, 1.2F, 4.0F, 10, 20);
    public static final DeferredHolder<EntityType<?>, EntityType<AbyssalMawEntity>> ABYSSAL_MAW =
            reg("fd_abyssal_maw", AbyssalMawEntity::new, 3.0F, 2.0F, 10, 20);
    public static final DeferredHolder<EntityType<?>, EntityType<GravityFieldEntity>> GRAVITY_FIELD =
            reg("fd_gravity_field", GravityFieldEntity::new, 1.0F, 0.3F, 10, 20);
    public static final DeferredHolder<EntityType<?>, EntityType<DimensionCutEntity>> DIMENSION_CUT =
            reg("fd_dimension_cut", DimensionCutEntity::new, 0.5F, 0.5F, 12, 20);
    public static final DeferredHolder<EntityType<?>, EntityType<VoidTearEntity>> VOID_TEAR =
            reg("fd_void_tear", VoidTearEntity::new, 1.0F, 3.0F, 12, 20);
    public static final DeferredHolder<EntityType<?>, EntityType<TwilightPillarEntity>> TWILIGHT_PILLAR =
            reg("fd_twilight_pillar", TwilightPillarEntity::new, 1.0F, 4.0F, 12, 20);
    public static final DeferredHolder<EntityType<?>, EntityType<YinYangEntity>> YIN_YANG =
            reg("fd_yin_yang", YinYangEntity::new, 1.0F, 1.0F, 10, 2);

    /** Queimar Alma: a chama da alma (ícone e cor; o dano é do rastreador {@code SoulBurn}). Água não apaga. */
    public static final DeferredHolder<MobEffect, MobEffect> SOUL_BURN = EFFECTS.register("fd_soul_burn", SoulBurnEffect::new);

    /** Corte Dimensional: ignora armadura (tag minecraft:bypasses_armor no merge). */
    public static final ResourceKey<DamageType> DIMENSION_SLASH_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, IsekaiMahou.id("fd_dimension_slash"));

    private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> reg(String id, EntityType.EntityFactory<T> f,
                                                                                    float w, float h, int range, int interval) {
        return ENTITIES.register(id, () -> EntityType.Builder.of(f, MobCategory.MISC).sized(w, h).noSave().noSummon()
                .fireImmune().clientTrackingRange(range).updateInterval(interval).build(id));
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
    }

    private FdRegistries() {}
}
