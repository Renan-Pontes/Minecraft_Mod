package com.renanpontes.isekaimahou.feature.fusions_b;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fusions_b.effect.FbMobEffects;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.AbyssTentacleEntity;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.CrystalPrisonEntity;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.CrystalShardEntity;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.EclipseFieldEntity;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.FallingStarEntity;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.GravityWellEntity;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.HellfireImpEntity;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.InfernalChainEntity;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.OrbitingDebrisEntity;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.TwilightCloneEntity;
import com.renanpontes.isekaimahou.feature.fusions_b.entity.VoidRiftEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registros do módulo de fusões B (Cristal, Astral, Chama Infernal, Abismo, Gravidade, Vazio, Crepúsculo):
 * entidades de magia, invocações, efeitos de status e tipos de dano.
 */
public final class FbRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    // =====================================================================================
    //  Entidades
    // =====================================================================================
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    /** Prisão de cristal que envolve o alvo de Cristalizar (só visual + estilhaço no fim). */
    public static final DeferredHolder<EntityType<?>, EntityType<CrystalPrisonEntity>> CRYSTAL_PRISON = ENTITIES.register("crystal_prison",
            () -> EntityType.Builder.<CrystalPrisonEntity>of(CrystalPrisonEntity::new, MobCategory.MISC)
                    .sized(1.0F, 2.0F).noSave().noSummon().fireImmune().clientTrackingRange(10).updateInterval(2)
                    .build("crystal_prison"));

    /** Fragmento de cristal disparado em leque por Estilhaços. */
    public static final DeferredHolder<EntityType<?>, EntityType<CrystalShardEntity>> CRYSTAL_SHARD = ENTITIES.register("crystal_shard",
            () -> EntityType.Builder.<CrystalShardEntity>of(CrystalShardEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).noSave().noSummon().clientTrackingRange(6).updateInterval(5)
                    .build("crystal_shard"));

    /** Estrela Cadente: cai do céu no ponto marcado. */
    public static final DeferredHolder<EntityType<?>, EntityType<FallingStarEntity>> FALLING_STAR = ENTITIES.register("falling_star",
            () -> EntityType.Builder.<FallingStarEntity>of(FallingStarEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).noSave().noSummon().fireImmune().clientTrackingRange(12).updateInterval(2)
                    .build("falling_star"));

    /** Correntes Infernais que saem do chão e prendem o alvo. */
    public static final DeferredHolder<EntityType<?>, EntityType<InfernalChainEntity>> INFERNAL_CHAIN = ENTITIES.register("infernal_chain",
            () -> EntityType.Builder.<InfernalChainEntity>of(InfernalChainEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).noSave().noSummon().fireImmune().clientTrackingRange(10).updateInterval(2)
                    .build("infernal_chain"));

    /** Diabrete da Chama Infernal invocado pelo Portal Infernal. */
    public static final DeferredHolder<EntityType<?>, EntityType<HellfireImpEntity>> HELLFIRE_IMP = ENTITIES.register("hellfire_imp",
            () -> EntityType.Builder.of(HellfireImpEntity::new, MobCategory.MISC)
                    .sized(0.55F, 1.05F).eyeHeight(0.85F).fireImmune().clientTrackingRange(10)
                    .build("hellfire_imp"));

    /** Tentáculos do Abismo em volta de um alvo. */
    public static final DeferredHolder<EntityType<?>, EntityType<AbyssTentacleEntity>> ABYSS_TENTACLE = ENTITIES.register("abyss_tentacle",
            () -> EntityType.Builder.<AbyssTentacleEntity>of(AbyssTentacleEntity::new, MobCategory.MISC)
                    .sized(1.0F, 2.0F).noSave().noSummon().clientTrackingRange(10).updateInterval(2)
                    .build("abyss_tentacle"));

    /** Ponto de Atração: pequeno buraco negro que puxa tudo. */
    public static final DeferredHolder<EntityType<?>, EntityType<GravityWellEntity>> GRAVITY_WELL = ENTITIES.register("gravity_well",
            () -> EntityType.Builder.<GravityWellEntity>of(GravityWellEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).noSave().noSummon().fireImmune().clientTrackingRange(12).updateInterval(4)
                    .build("gravity_well"));

    /** Destroço de terra/pedra que orbita o conjurador. */
    public static final DeferredHolder<EntityType<?>, EntityType<OrbitingDebrisEntity>> ORBITING_DEBRIS = ENTITIES.register("orbiting_debris",
            () -> EntityType.Builder.<OrbitingDebrisEntity>of(OrbitingDebrisEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).noSave().noSummon().fireImmune().clientTrackingRange(10).updateInterval(1)
                    .build("orbiting_debris"));

    /** Rasgo do Vazio: fenda vertical que fere quem está perto. */
    public static final DeferredHolder<EntityType<?>, EntityType<VoidRiftEntity>> VOID_RIFT = ENTITIES.register("void_rift",
            () -> EntityType.Builder.<VoidRiftEntity>of(VoidRiftEntity::new, MobCategory.MISC)
                    .sized(1.2F, 3.0F).noSave().noSummon().fireImmune().clientTrackingRange(12).updateInterval(10)
                    .build("void_rift"));

    /** Clone de luz ou de sombra criado por Dualidade. */
    public static final DeferredHolder<EntityType<?>, EntityType<TwilightCloneEntity>> TWILIGHT_CLONE = ENTITIES.register("twilight_clone",
            () -> EntityType.Builder.of(TwilightCloneEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(10)
                    .build("twilight_clone"));

    /** Campo do Eclipse: disco escuro no céu e escuridão no chão. */
    public static final DeferredHolder<EntityType<?>, EntityType<EclipseFieldEntity>> ECLIPSE_FIELD = ENTITIES.register("eclipse_field",
            () -> EntityType.Builder.<EclipseFieldEntity>of(EclipseFieldEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).noSave().noSummon().fireImmune().clientTrackingRange(12).updateInterval(10)
                    .build("eclipse_field"));

    // =====================================================================================
    //  Efeitos de status
    // =====================================================================================
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);

    /** Cristalizado: preso num bloco de cristal (não anda, não pula, quase não ataca). */
    public static final DeferredHolder<MobEffect, MobEffect> CRYSTALLIZED = EFFECTS.register("crystallized", FbMobEffects::crystallized);
    /** Armadura de Cristal: armadura extra e reflete parte do dano corpo a corpo. */
    public static final DeferredHolder<MobEffect, MobEffect> CRYSTAL_ARMOR = EFFECTS.register("crystal_armor", FbMobEffects::crystalArmor);
    /** Prisma Refletor: devolve projéteis que chegam perto. */
    public static final DeferredHolder<MobEffect, MobEffect> PRISM_REFLECTION = EFFECTS.register("prism_reflection", FbMobEffects.PrismReflection::new);
    /** Gravidade Zero: sem gravidade (jogadores sobem com pulo e descem agachando). */
    public static final DeferredHolder<MobEffect, MobEffect> ZERO_GRAVITY = EFFECTS.register("zero_gravity", FbMobEffects::zeroGravity);
    /** Projeção Astral: espírito fora do corpo (indicador e cronômetro). */
    public static final DeferredHolder<MobEffect, MobEffect> ASTRAL_PROJECTION = EFFECTS.register("astral_projection", FbMobEffects.AstralProjectionEffect::new);
    /** Fogo Negro: queima mesmo na água e drena vida para quem conjurou. */
    public static final DeferredHolder<MobEffect, MobEffect> BLACK_FLAME = EFFECTS.register("black_flame", FbMobEffects.BlackFlame::new);
    /** Pacto de Sangue: mais dano e poder mágico. */
    public static final DeferredHolder<MobEffect, MobEffect> BLOOD_PACT = EFFECTS.register("blood_pact", FbMobEffects::bloodPact);
    /** Veneno Abissal: dano contínuo que pode matar e afeta mortos-vivos. */
    public static final DeferredHolder<MobEffect, MobEffect> ABYSSAL_VENOM = EFFECTS.register("abyssal_venom", FbMobEffects.AbyssalVenom::new);
    /** Afogamento Abissal: perde o ar mesmo fora da água. */
    public static final DeferredHolder<MobEffect, MobEffect> ABYSSAL_DROWN = EFFECTS.register("abyssal_drown", FbMobEffects.AbyssalDrown::new);
    /** Tinta: perde o alvo de vista (alcance de visão muito menor). */
    public static final DeferredHolder<MobEffect, MobEffect> INKED = EFFECTS.register("inked", FbMobEffects::inked);
    /** Gravidade Invertida: cai para cima. */
    public static final DeferredHolder<MobEffect, MobEffect> INVERTED_GRAVITY = EFFECTS.register("inverted_gravity", FbMobEffects.InvertedGravity::new);

    // =====================================================================================
    //  Tipos de dano (data/isekai_mahou/damage_type/*.json)
    // =====================================================================================
    public static final ResourceKey<DamageType> BLACK_FLAME_DAMAGE = key("black_flame");
    public static final ResourceKey<DamageType> ABYSSAL_VENOM_DAMAGE = key("abyssal_venom");
    public static final ResourceKey<DamageType> ABYSSAL_DROWN_DAMAGE = key("abyssal_drown");
    /** Apagar: ignora armadura (tag minecraft:bypasses_armor no merge). */
    public static final ResourceKey<DamageType> VOID_ERASE_DAMAGE = key("void_erase");
    public static final ResourceKey<DamageType> GRAVITY_CRASH_DAMAGE = key("gravity_crash");

    private static ResourceKey<DamageType> key(String id) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, IsekaiMahou.id(id));
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener(FbRegistries::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(HELLFIRE_IMP.get(), HellfireImpEntity.createAttributes().build());
        e.put(TWILIGHT_CLONE.get(), TwilightCloneEntity.createAttributes().build());
    }

    private FbRegistries() {}
}
