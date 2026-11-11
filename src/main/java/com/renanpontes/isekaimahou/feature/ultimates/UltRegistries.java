package com.renanpontes.isekaimahou.feature.ultimates;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.ultimates.effect.TranscendenceEffect;
import com.renanpontes.isekaimahou.feature.ultimates.entity.MeteorEntity;
import com.renanpontes.isekaimahou.feature.ultimates.entity.SpectralColossusEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entidades e efeito do módulo "ultimates". Nenhuma entidade é salva: somem ao descarregar o chunk. */
public final class UltRegistries {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    /** Meteoro: rocha incandescente que cai do céu (rastreado de longe — cai de 40 blocos de altura). */
    public static final DeferredHolder<EntityType<?>, EntityType<MeteorEntity>> METEOR = ENTITIES.register("meteor",
            () -> EntityType.Builder.<MeteorEntity>of(MeteorEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().noSave().clientTrackingRange(12).updateInterval(5).build("meteor"));

    /** Colosso Espectral: gigante de ~12 blocos que acompanha o dono (não é uma criatura: não leva dano). */
    public static final DeferredHolder<EntityType<?>, EntityType<SpectralColossusEntity>> SPECTRAL_COLOSSUS =
            ENTITIES.register("spectral_colossus",
                    () -> EntityType.Builder.<SpectralColossusEntity>of(SpectralColossusEntity::new, MobCategory.MISC)
                            .sized(3.0F, 12.0F).fireImmune().noSave().clientTrackingRange(12).updateInterval(1)
                            .build("spectral_colossus"));

    /** Transcendência (indicador da transformação). */
    public static final DeferredHolder<MobEffect, MobEffect> TRANSCENDENCE = EFFECTS.register("transcendence", TranscendenceEffect::new);

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
    }

    private UltRegistries() {}
}
