package com.renanpontes.isekaimahou.feature.arcane_duel;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.arcane_duel.effect.DuelStatusEffect;
import com.renanpontes.isekaimahou.feature.arcane_duel.entity.DuelFxEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo arcane_duel: efeitos Resguardo/Indestrutível e a entidade de visual dos duelos. */
public final class DuelRegistry {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);

    /** Resguardo: imune a status negativos e a contramágica; magias inimigas cobram "imposto" de mana do atacante. */
    public static final DeferredHolder<MobEffect, MobEffect> HEXPROOF = EFFECTS.register("hexproof",
            () -> new DuelStatusEffect(MobEffectCategory.BENEFICIAL, 0x7FE3FF, DuelStatusEffect.Aura.HEXPROOF));

    /** Indestrutível: a vida não cai abaixo de 1. */
    public static final DeferredHolder<MobEffect, MobEffect> INDESTRUCTIBLE = EFFECTS.register("indestructible",
            () -> new DuelStatusEffect(MobEffectCategory.BENEFICIAL, 0xFFD24A, DuelStatusEffect.Aura.INDESTRUCTIBLE));

    /**
     * Visual dos glifos de duelo (vidro mágico, resguardo, égide, fenda, sigilo do céu, pilares, relógio, cartas...).
     * Não é salvo: some sozinho e também ao descarregar o chunk.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<DuelFxEntity>> FX = ENTITIES.register("arcane_duel_fx",
            () -> EntityType.Builder.<DuelFxEntity>of(DuelFxEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSave().noSummon()
                    .clientTrackingRange(10).updateInterval(4).build("arcane_duel_fx"));

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
        ENTITIES.register(modBus);
    }

    private DuelRegistry() {}
}
