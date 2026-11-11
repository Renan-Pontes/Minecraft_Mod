package com.renanpontes.isekaimahou.feature.domain;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.effect.MagicEffect;
import com.renanpontes.isekaimahou.feature.domain.entity.DomainDomeEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo de Domínio: a entidade do domo e o efeito "Peso do Domínio". */
public final class DomainRegistries {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    /** Domo de Domínio (Expansão de Domínio e Domínio Simples). Rastreado de longe: o domo é grande. */
    public static final DeferredHolder<EntityType<?>, EntityType<DomainDomeEntity>> DOMAIN_DOME = ENTITIES.register("domain_dome",
            () -> EntityType.Builder.<DomainDomeEntity>of(DomainDomeEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(2).fireImmune().noSummon()
                    .build("domain_dome"));

    private static final ResourceLocation GRAVITY_MOD = IsekaiMahou.id("effect.domain_gravity");

    /** Lei "Gravidade do Domínio": sem pulo, cai mais rápido, anda mais devagar e se machuca mais na queda. */
    public static final DeferredHolder<MobEffect, MobEffect> DOMAIN_GRAVITY = EFFECTS.register("domain_gravity",
            () -> new MagicEffect(MobEffectCategory.HARMFUL, 0x4B3B8F)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, GRAVITY_MOD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.GRAVITY, GRAVITY_MOD, 0.75, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, GRAVITY_MOD, -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, GRAVITY_MOD, -2.0, AttributeModifier.Operation.ADD_VALUE));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
    }

    private DomainRegistries() {}
}
