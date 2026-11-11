package com.renanpontes.isekaimahou.feature.fire_water;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.fire_water.effect.FwMobEffects;
import com.renanpontes.isekaimahou.feature.fire_water.entity.ElementalFieldEntity;
import com.renanpontes.isekaimahou.feature.fire_water.entity.SummonedSalamander;
import com.renanpontes.isekaimahou.feature.fire_water.entity.SummonedUndine;
import com.renanpontes.isekaimahou.feature.fire_water.entity.UndineWaterJet;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo Fogo/Água: entidades (campos, invocações, jato), efeitos de status e tipos de dano. */
public final class FwRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    // ---------------- Entidades ----------------
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    /** Campo elemental (Fumaça, Névoa, Chuva Local, Dilúvio, Muralha de Fogo): invisível, só partículas. */
    public static final DeferredHolder<EntityType<?>, EntityType<ElementalFieldEntity>> FIELD = ENTITIES.register("fire_water_field",
            () -> EntityType.Builder.<ElementalFieldEntity>of(ElementalFieldEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSummon().clientTrackingRange(10).updateInterval(10)
                    .build("fire_water_field"));

    public static final DeferredHolder<EntityType<?>, EntityType<SummonedSalamander>> SALAMANDER = ENTITIES.register("summoned_salamander",
            () -> EntityType.Builder.of(SummonedSalamander::new, MobCategory.MISC)
                    .sized(0.9F, 0.65F).eyeHeight(0.45F).fireImmune().clientTrackingRange(10)
                    .build("summoned_salamander"));

    public static final DeferredHolder<EntityType<?>, EntityType<SummonedUndine>> UNDINE = ENTITIES.register("summoned_undine",
            () -> EntityType.Builder.of(SummonedUndine::new, MobCategory.MISC)
                    .sized(0.7F, 1.9F).eyeHeight(1.6F).clientTrackingRange(10)
                    .build("summoned_undine"));

    public static final DeferredHolder<EntityType<?>, EntityType<UndineWaterJet>> WATER_JET = ENTITIES.register("undine_water_jet",
            () -> EntityType.Builder.<UndineWaterJet>of(UndineWaterJet::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).noSummon().clientTrackingRange(6).updateInterval(5)
                    .build("undine_water_jet"));

    // ---------------- Efeitos de status ----------------
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    /** Brasa Persistente: dano contínuo que reacende o alvo. */
    public static final DeferredHolder<MobEffect, MobEffect> LINGERING_EMBER = EFFECTS.register("lingering_ember", FwMobEffects.LingeringEmber::new);
    /** Chama Azul: fogo de alma que não apaga na água e queima até criaturas de fogo. */
    public static final DeferredHolder<MobEffect, MobEffect> AZURE_FLAME = EFFECTS.register("azure_flame", FwMobEffects.AzureFlame::new);
    /** Bolha: preso numa bolha flutuante. */
    public static final DeferredHolder<MobEffect, MobEffect> BUBBLE_PRISON = EFFECTS.register("bubble_prison", FwMobEffects.BubblePrison::new);
    /** Correnteza: arrastado por uma corrente de água. */
    public static final DeferredHolder<MobEffect, MobEffect> UNDERTOW = EFFECTS.register("undertow", FwMobEffects.Undertow::new);
    /** Afogando: perde o ar mesmo fora da água. */
    public static final DeferredHolder<MobEffect, MobEffect> WATER_CHOKE = EFFECTS.register("water_choke", FwMobEffects.WaterChoke::new);
    /** Espelho d'Água: reflete o próximo projétil. */
    public static final DeferredHolder<MobEffect, MobEffect> WATER_MIRROR = EFFECTS.register("water_mirror", FwMobEffects.WaterMirror::new);
    /** Bênção da Fênix: renasce ao morrer. */
    public static final DeferredHolder<MobEffect, MobEffect> PHOENIX_BLESSING = EFFECTS.register("phoenix_blessing", FwMobEffects.PhoenixBlessing::new);

    // ---------------- Itens: ovos (aba Criaturas) ----------------
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    /** Ovo da Salamandra: a criatura nasce ligada ao jogador mais próximo (quem usou o ovo). */
    public static final DeferredItem<DeferredSpawnEggItem> SALAMANDER_EGG = ITEMS.register("summoned_salamander_spawn_egg",
            () -> new DeferredSpawnEggItem(SALAMANDER, 0xB8361A, 0xFFB43A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> UNDINE_EGG = ITEMS.register("summoned_undine_spawn_egg",
            () -> new DeferredSpawnEggItem(UNDINE, 0x2F7FD8, 0xCFF3FF, new Item.Properties()));

    // ---------------- Tipos de dano (data/isekai_mahou/damage_type/*.json) ----------------
    public static final ResourceKey<DamageType> EMBER_BURN = ResourceKey.create(Registries.DAMAGE_TYPE, IsekaiMahou.id("ember_burn"));
    public static final ResourceKey<DamageType> AZURE_BURN = ResourceKey.create(Registries.DAMAGE_TYPE, IsekaiMahou.id("azure_flame"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(FwRegistries::attributes);
        modBus.addListener(FwRegistries::creativeTabs);
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(SALAMANDER_EGG.get());
            e.accept(UNDINE_EGG.get());
        }
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(SALAMANDER.get(), SummonedSalamander.createAttributes().build());
        e.put(UNDINE.get(), SummonedUndine.createAttributes().build());
    }

    private FwRegistries() {}
}
