package com.renanpontes.isekaimahou.feature.legends_a;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_a.entity.ArsenalPortalEntity;
import com.renanpontes.isekaimahou.feature.legends_a.entity.ConjuredBladeEntity;
import com.renanpontes.isekaimahou.feature.legends_a.entity.CraterBlastEntity;
import com.renanpontes.isekaimahou.feature.legends_a.entity.ExplosionSigilEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entidades das Magias Lendárias A (todas visuais/temporárias: não são salvas no mundo). */
public final class LegendsARegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);

    /**
     * Arma conjurada (voando, cravada no chão ou brotando do Campo de Lâminas). O cliente prevê o voo em linha reta;
     * o servidor só corrige a cada 10 ticks (como as flechas) — toda troca de modo/direção já manda a posição na hora.
     * Com correção a cada 2 ticks, a arma "pulava para trás" em multijogador (atraso × 2 blocos/tick).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ConjuredBladeEntity>> CONJURED_BLADE = ENTITIES.register("conjured_blade",
            () -> EntityType.Builder.<ConjuredBladeEntity>of(ConjuredBladeEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSave().clientTrackingRange(8).updateInterval(10).build("conjured_blade"));

    /** Portal dourado do Portão do Arsenal. */
    public static final DeferredHolder<EntityType<?>, EntityType<ArsenalPortalEntity>> ARSENAL_PORTAL = ENTITIES.register("arsenal_portal",
            () -> EntityType.Builder.<ArsenalPortalEntity>of(ArsenalPortalEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().noSave().clientTrackingRange(8).updateInterval(2).build("arsenal_portal"));

    /** Círculos mágicos empilhados no céu (conjuração da Explosão Colossal). */
    public static final DeferredHolder<EntityType<?>, EntityType<ExplosionSigilEntity>> EXPLOSION_SIGIL = ENTITIES.register("explosion_sigil",
            () -> EntityType.Builder.<ExplosionSigilEntity>of(ExplosionSigilEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().noSave().clientTrackingRange(16).updateInterval(1).build("explosion_sigil"));

    /** Bola de fogo, coluna, cogumelo e onda de choque da Explosão Colossal (e quem abre a cratera). */
    public static final DeferredHolder<EntityType<?>, EntityType<CraterBlastEntity>> CRATER_BLAST = ENTITIES.register("crater_blast",
            () -> EntityType.Builder.<CraterBlastEntity>of(CraterBlastEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).fireImmune().noSave().clientTrackingRange(16).updateInterval(20).build("crater_blast"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    private LegendsARegistry() {}
}
