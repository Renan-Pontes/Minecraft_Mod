package com.renanpontes.isekaimahou.feature.legends_e;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.legends_e.entity.BlackGoatStormEntity;
import com.renanpontes.isekaimahou.feature.legends_e.entity.DarkYoungEntity;
import com.renanpontes.isekaimahou.feature.legends_e.entity.DeathClockEntity;
import com.renanpontes.isekaimahou.feature.legends_e.entity.FallenPillarEntity;
import com.renanpontes.isekaimahou.feature.legends_e.entity.GraspHeartEntity;
import com.renanpontes.isekaimahou.feature.legends_e.entity.SupremeCircleEntity;
import com.renanpontes.isekaimahou.feature.legends_e.item.ArcaneHourglassItem;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registros do módulo legends_e: as entidades das Magias de Nível Supremo (todas temporárias: {@code noSave}, somem ao
 * descarregar o chunk), o Filhote Sombrio, a Ampulheta Arcana e o ovo do Filhote.
 */
public final class LeRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    /** Círculos mágicos enormes da Magia de Nível Supremo (nos pés do conjurador; desenha também o círculo no céu do alvo). */
    public static final DeferredHolder<EntityType<?>, EntityType<SupremeCircleEntity>> SUPREME_CIRCLE = ENTITIES.register("supreme_circle",
            () -> EntityType.Builder.<SupremeCircleEntity>of(SupremeCircleEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSave().clientTrackingRange(12).updateInterval(10).build("supreme_circle"));

    /** Queda Final: o pilar de luz que desce do céu. */
    public static final DeferredHolder<EntityType<?>, EntityType<FallenPillarEntity>> FALLEN_PILLAR = ENTITIES.register("fallen_down_pillar",
            () -> EntityType.Builder.<FallenPillarEntity>of(FallenPillarEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSave().clientTrackingRange(12).updateInterval(20).build("fallen_down_pillar"));

    /** Iä! Cabra Negra: o vento negro e o selo do sacrifício. */
    public static final DeferredHolder<EntityType<?>, EntityType<BlackGoatStormEntity>> BLACK_GOAT_STORM = ENTITIES.register("black_goat_storm",
            () -> EntityType.Builder.<BlackGoatStormEntity>of(BlackGoatStormEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSave().clientTrackingRange(10).updateInterval(20).build("black_goat_storm"));

    /** Agarrar o Coração: a mão espectral que segue o alvo. */
    public static final DeferredHolder<EntityType<?>, EntityType<GraspHeartEntity>> GRASP_HEART = ENTITIES.register("grasp_heart_hand",
            () -> EntityType.Builder.<GraspHeartEntity>of(GraspHeartEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.8F).fireImmune().noSave().clientTrackingRange(8).updateInterval(1).build("grasp_heart_hand"));

    /** O Fim de Toda Vida: o relógio gigante no céu. */
    public static final DeferredHolder<EntityType<?>, EntityType<DeathClockEntity>> DEATH_CLOCK = ENTITIES.register("death_clock",
            () -> EntityType.Builder.<DeathClockEntity>of(DeathClockEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSave().clientTrackingRange(12).updateInterval(20).build("death_clock"));

    /** Filhote Sombrio (~4 blocos). Invocação temporária: não é salvo. */
    public static final DeferredHolder<EntityType<?>, EntityType<DarkYoungEntity>> DARK_YOUNG = ENTITIES.register("dark_young",
            () -> EntityType.Builder.of(DarkYoungEntity::new, MobCategory.MISC)
                    .sized(2.0F, 4.1F).fireImmune().noSave().clientTrackingRange(10).build("dark_young"));

    public static final DeferredItem<ArcaneHourglassItem> ARCANE_HOURGLASS = ITEMS.register("arcane_hourglass",
            () -> new ArcaneHourglassItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)
                    .component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)));

    public static final DeferredItem<DeferredSpawnEggItem> DARK_YOUNG_EGG = ITEMS.register("dark_young_spawn_egg",
            () -> new DeferredSpawnEggItem(DARK_YOUNG, 0x14101A, 0x7DFF4A, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(LeRegistry::tabs);
        modBus.addListener(LeRegistry::attributes);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) e.accept(ARCANE_HOURGLASS.get());
        if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) e.accept(DARK_YOUNG_EGG.get());
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(DARK_YOUNG.get(), DarkYoungEntity.createAttributes().build());
    }

    private LeRegistry() {}
}
