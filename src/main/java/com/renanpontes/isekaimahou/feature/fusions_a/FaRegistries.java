package com.renanpontes.isekaimahou.feature.fusions_a;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.fusions_a.block.ClearTempBlock;
import com.renanpontes.isekaimahou.feature.fusions_a.block.FaTempBlock;
import com.renanpontes.isekaimahou.feature.fusions_a.block.MoltenPoolBlock;
import com.renanpontes.isekaimahou.feature.fusions_a.block.SinkingSandBlock;
import com.renanpontes.isekaimahou.feature.fusions_a.effect.FaEffects;
import com.renanpontes.isekaimahou.feature.fusions_a.entity.FusionZoneEntity;
import com.renanpontes.isekaimahou.feature.fusions_a.entity.SummonedEnt;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo fusions_a: blocos temporários, zona das fusões, Ent invocado (e seu ovo) e efeitos de status. */
public final class FaRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    // ---------------- Blocos temporários (sem item: só existem enquanto a magia dura) ----------------
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);

    /** Poça de Lava: lâmina de magma que não escorre. */
    public static final DeferredBlock<MoltenPoolBlock> MOLTEN_POOL = BLOCKS.register("molten_pool",
            () -> new MoltenPoolBlock(BlockBehaviour.Properties.of().mapColor(MapColor.FIRE).noCollission()
                    .strength(0.4F).lightLevel(s -> 13).sound(SoundType.MUD).noLootTable()
                    .pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, t) -> false)));

    /** Obsidiana mágica: prisão/plataforma temporária. */
    public static final DeferredBlock<FaTempBlock> MAGMA_PRISON = BLOCKS.register("magma_prison",
            () -> new FaTempBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(20F, 1200F).lightLevel(s -> 5).sound(SoundType.STONE).noLootTable()
                    .pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, t) -> false),
                    Blocks.AIR::defaultBlockState));

    /** Gelo transparente: Ponte de Gelo e Estátua de Gelo. */
    public static final DeferredBlock<ClearTempBlock> FROST_ICE = BLOCKS.register("frost_ice",
            () -> new ClearTempBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).friction(0.98F)
                    .strength(0.6F).sound(SoundType.GLASS).noOcclusion().noLootTable()
                    .pushReaction(PushReaction.BLOCK).isValidSpawn((s, l, p, t) -> false)
                    .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false),
                    Blocks.AIR::defaultBlockState, () -> ParticleTypes.SNOWFLAKE));

    /** Gelo liso que substitui o chão (Chão Escorregadio). */
    public static final DeferredBlock<FaTempBlock> SLICK_ICE = BLOCKS.register("slick_ice",
            () -> new FaTempBlock(BlockBehaviour.Properties.of().mapColor(MapColor.ICE).friction(0.989F)
                    .strength(0.5F).sound(SoundType.GLASS).noLootTable().pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((s, l, p, t) -> false),
                    Blocks.DIRT::defaultBlockState));

    /** Areia Movediça Maior. */
    public static final DeferredBlock<SinkingSandBlock> SINKING_SAND = BLOCKS.register("sinking_sand",
            () -> new SinkingSandBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5F)
                    .sound(SoundType.SAND).noLootTable().pushReaction(PushReaction.BLOCK).dynamicShape()
                    .isSuffocating((s, l, p) -> false).isValidSpawn((s, l, p, t) -> false)));

    /** Vidro de Vitrificar (prisão). */
    public static final DeferredBlock<ClearTempBlock> GLASS_PRISON = BLOCKS.register("glass_prison",
            () -> new ClearTempBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.8F)
                    .sound(SoundType.GLASS).noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false),
                    Blocks.AIR::defaultBlockState,
                    () -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState())));

    // ---------------- Entidades ----------------
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<FusionZoneEntity>> FUSION_ZONE = ENTITIES.register("fusion_zone",
            () -> EntityType.Builder.<FusionZoneEntity>of(FusionZoneEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSummon().clientTrackingRange(10).updateInterval(2)
                    .build("fusion_zone"));

    public static final DeferredHolder<EntityType<?>, EntityType<SummonedEnt>> SUMMONED_ENT = ENTITIES.register("summoned_ent",
            () -> EntityType.Builder.of(SummonedEnt::new, MobCategory.MISC)
                    .sized(1.3F, 2.9F).eyeHeight(2.45F).clientTrackingRange(10)
                    .build("summoned_ent"));

    // ---------------- Itens: ovo do Ent (aba Criaturas; o Ent nasce ligado a quem usou o ovo) ----------------
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<DeferredSpawnEggItem> SUMMONED_ENT_EGG = ITEMS.register("summoned_ent_spawn_egg",
            () -> new DeferredSpawnEggItem(SUMMONED_ENT, 0x5A3E22, 0x4FBF4A, new Item.Properties()));

    // ---------------- Efeitos de status ----------------
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    public static final DeferredHolder<MobEffect, MobEffect> MAGMA_ARMOR = EFFECTS.register("magma_armor", FaEffects.MagmaArmor::new);
    public static final DeferredHolder<MobEffect, MobEffect> ICE_ARMOR = EFFECTS.register("ice_armor", FaEffects.IceArmor::new);
    public static final DeferredHolder<MobEffect, MobEffect> OVERCHARGE = EFFECTS.register("overcharge", FaEffects.Overcharge::new);
    public static final DeferredHolder<MobEffect, MobEffect> SOLAR_CROWN = EFFECTS.register("solar_crown", FaEffects.SolarCrown::new);
    public static final DeferredHolder<MobEffect, MobEffect> PHOTOSYNTHESIS = EFFECTS.register("photosynthesis", FaEffects.Photosynthesis::new);
    public static final DeferredHolder<MobEffect, MobEffect> PROTECTIVE_BUBBLE = EFFECTS.register("protective_bubble", FaEffects.ProtectiveBubble::new);
    public static final DeferredHolder<MobEffect, MobEffect> HOLY_FLAME = EFFECTS.register("holy_flame", FaEffects.HolyFlame::new);
    public static final DeferredHolder<MobEffect, MobEffect> ICE_STATUE = EFFECTS.register("ice_statue", FaEffects.IceStatue::new);
    public static final DeferredHolder<MobEffect, MobEffect> SPORE_SLEEP = EFFECTS.register("spore_sleep", FaEffects.SporeSleep::new);
    public static final DeferredHolder<MobEffect, MobEffect> BEWILDERED = EFFECTS.register("bewildered", FaEffects.Bewildered::new);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(FaRegistries::attributes);
        modBus.addListener(FaRegistries::creativeTabs);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) e.accept(SUMMONED_ENT_EGG.get());
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(SUMMONED_ENT.get(), SummonedEnt.createAttributes().build());
    }

    private FaRegistries() {}
}
