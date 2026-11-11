package com.renanpontes.isekaimahou.feature.forms_b;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.forms_b.block.SpellBarrierBlock;
import com.renanpontes.isekaimahou.feature.forms_b.block.SpellBarrierBlockEntity;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellConjurerEntity;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellIllusionEntity;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellOrbEntity;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellPortalEntity;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellRuneEntity;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellTurretEntity;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellWallEntity;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellZoneEntity;
import com.renanpontes.isekaimahou.feature.forms_b.seal.SealData;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo forms_b: bloco da Muralha, entidades das formas e o componente do Selo. */
public final class FormsBRegistry {
    private static final String MODID = IsekaiMahou.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    // ---------------- Bloco temporário da Muralha ----------------
    public static final DeferredBlock<SpellBarrierBlock> SPELL_BARRIER = BLOCKS.register("spell_barrier",
            () -> new SpellBarrierBlock(SpellBarrierBlock.barrierProperties()));
    public static final DeferredItem<BlockItem> SPELL_BARRIER_ITEM = ITEMS.registerSimpleBlockItem("spell_barrier", SPELL_BARRIER,
            new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpellBarrierBlockEntity>> SPELL_BARRIER_BE =
            BLOCK_ENTITIES.register("spell_barrier", () -> BlockEntityType.Builder.of(SpellBarrierBlockEntity::new, SPELL_BARRIER.get()).build(null));

    // ---------------- Entidades das formas ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<SpellZoneEntity>> SPELL_ZONE = ENTITIES.register("spell_zone",
            () -> EntityType.Builder.<SpellZoneEntity>of(SpellZoneEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.2F).fireImmune().clientTrackingRange(10).updateInterval(20).build("spell_zone"));
    public static final DeferredHolder<EntityType<?>, EntityType<SpellWallEntity>> SPELL_WALL = ENTITIES.register("spell_wall",
            () -> EntityType.Builder.<SpellWallEntity>of(SpellWallEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().clientTrackingRange(6).updateInterval(40).build("spell_wall"));
    public static final DeferredHolder<EntityType<?>, EntityType<SpellRuneEntity>> SPELL_RUNE = ENTITIES.register("spell_rune",
            () -> EntityType.Builder.<SpellRuneEntity>of(SpellRuneEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.1F).fireImmune().clientTrackingRange(6).updateInterval(40).build("spell_rune"));
    public static final DeferredHolder<EntityType<?>, EntityType<SpellOrbEntity>> SPELL_ORB = ENTITIES.register("spell_orb",
            () -> EntityType.Builder.<SpellOrbEntity>of(SpellOrbEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).fireImmune().clientTrackingRange(6).updateInterval(10).build("spell_orb"));
    public static final DeferredHolder<EntityType<?>, EntityType<SpellTurretEntity>> SPELL_TURRET = ENTITIES.register("spell_turret",
            () -> EntityType.Builder.<SpellTurretEntity>of(SpellTurretEntity::new, MobCategory.MISC)
                    .sized(0.8F, 1.6F).fireImmune().clientTrackingRange(8).updateInterval(5).build("spell_turret"));
    public static final DeferredHolder<EntityType<?>, EntityType<SpellConjurerEntity>> SPELL_CONJURER = ENTITIES.register("spell_conjurer",
            () -> EntityType.Builder.<SpellConjurerEntity>of(SpellConjurerEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.0F).fireImmune().clientTrackingRange(8).updateInterval(4).build("spell_conjurer"));
    public static final DeferredHolder<EntityType<?>, EntityType<SpellIllusionEntity>> SPELL_ILLUSION = ENTITIES.register("spell_illusion",
            () -> EntityType.Builder.<SpellIllusionEntity>of(SpellIllusionEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).fireImmune().clientTrackingRange(8).build("spell_illusion"));
    public static final DeferredHolder<EntityType<?>, EntityType<SpellPortalEntity>> SPELL_PORTAL = ENTITIES.register("spell_portal",
            () -> EntityType.Builder.<SpellPortalEntity>of(SpellPortalEntity::new, MobCategory.MISC)
                    .sized(1.2F, 2.3F).fireImmune().clientTrackingRange(10).updateInterval(40).build("spell_portal"));

    // ---------------- Componente do Selo ----------------
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SealData>> WEAPON_SEAL = COMPONENTS.registerComponentType("weapon_seal",
            b -> b.persistent(SealData.CODEC).networkSynchronized(SealData.STREAM_CODEC));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
    }

    private FormsBRegistry() {}
}
