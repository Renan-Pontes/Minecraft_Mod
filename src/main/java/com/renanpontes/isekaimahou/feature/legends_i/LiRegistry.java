package com.renanpontes.isekaimahou.feature.legends_i;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_i.effect.AmaterasuEffect;
import com.renanpontes.isekaimahou.feature.legends_i.effect.LiMobEffect;
import com.renanpontes.isekaimahou.feature.legends_i.entity.KageBunshinEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registros do legends_i: os efeitos de status dos glifos de anime (Susanoo, Amaterasu, Asas de Papel, Haki da
 * Observação) e a entidade Clone das Sombras (TEMPORÁRIA, tipo {@code noSave}).
 */
public final class LiRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    // ---------------------------------------------------------------- entidades
    /** Clone das Sombras (Kage Bunshin): cópia do conjurador que luta um pouco e some numa nuvem de fumaça. */
    public static final DeferredHolder<EntityType<?>, EntityType<KageBunshinEntity>> KAGE_BUNSHIN = ENTITIES.register("li_kage_bunshin",
            () -> EntityType.Builder.<KageBunshinEntity>of(KageBunshinEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).noSave().clientTrackingRange(10).build("li_kage_bunshin"));

    // ---------------------------------------------------------------- efeitos
    /** Susanoo: costelas espectrais em volta do corpo — dano recebido −35%, golpes com corte espectral de longo alcance. */
    public static final DeferredHolder<MobEffect, MobEffect> SUSANOO = EFFECTS.register("li_susanoo",
            () -> new LiMobEffect(MobEffectCategory.BENEFICIAL, 0x9A4DFF)
                    .addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id("effect.li_susanoo"), 4.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.li_susanoo"), 0.5,
                            AttributeModifier.Operation.ADD_VALUE));

    /** Amaterasu: chamas negras que queimam até o fim — a água não apaga. */
    public static final DeferredHolder<MobEffect, MobEffect> AMATERASU = EFFECTS.register("li_amaterasu",
            () -> new AmaterasuEffect(MobEffectCategory.HARMFUL, 0x1A0A1E));

    /** Asas de Papel (Konan): planar caindo devagar na direção em que olha; sem dano de queda. */
    public static final DeferredHolder<MobEffect, MobEffect> PAPER_WINGS = EFFECTS.register("li_paper_wings",
            () -> new LiMobEffect(MobEffectCategory.BENEFICIAL, 0xF2EEDD));

    /** Haki da Observação: vê presenças através das paredes e às vezes "vê o futuro" e desvia de um golpe. */
    public static final DeferredHolder<MobEffect, MobEffect> OBSERVATION = EFFECTS.register("li_observation",
            () -> new LiMobEffect(MobEffectCategory.BENEFICIAL, 0xE04848));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class,
                e -> e.put(KAGE_BUNSHIN.get(), KageBunshinEntity.createAttributes().build()));
    }

    private LiRegistry() {}
}
