package com.renanpontes.isekaimahou.feature.legends_c;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_c.entity.BlueOrbEntity;
import com.renanpontes.isekaimahou.feature.legends_c.entity.FugaArrowEntity;
import com.renanpontes.isekaimahou.feature.legends_c.entity.InfinityFieldEntity;
import com.renanpontes.isekaimahou.feature.legends_c.entity.MalevolentShrineEntity;
import com.renanpontes.isekaimahou.feature.legends_c.entity.RedOrbEntity;
import com.renanpontes.isekaimahou.feature.legends_c.entity.VoidFieldEntity;
import com.renanpontes.isekaimahou.feature.legends_c.infinity.MugenEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Entidades e o efeito do módulo legends_c (Técnicas Malditas). Todas as entidades são VISUAIS/temporárias e não são
 * salvas ({@code noSave}): somem sozinhas ao descarregar o chunk ou reiniciar o mundo, nunca ficam "presas" no mapa.
 */
public final class LcRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    /** Infinito (Mugen): o efeito guarda a duração (aparece no inventário) e roda a lógica a cada tick. */
    public static final DeferredHolder<MobEffect, MugenEffect> MUGEN = EFFECTS.register("mugen",
            () -> new MugenEffect(MobEffectCategory.BENEFICIAL, 0x9FD8FF));

    /** Santuário Maligno: o templo GeckoLib (~12 blocos) que aparece atrás do dono do domínio. */
    public static final DeferredHolder<EntityType<?>, EntityType<MalevolentShrineEntity>> SHRINE = ENTITIES.register("malevolent_shrine",
            () -> EntityType.Builder.<MalevolentShrineEntity>of(MalevolentShrineEntity::new, MobCategory.MISC)
                    .sized(2.0F, 4.0F).fireImmune().noSave().clientTrackingRange(16).updateInterval(20).build("malevolent_shrine"));

    /** Esfera de distorção do Infinito (segue o dono; segura projéteis). */
    public static final DeferredHolder<EntityType<?>, EntityType<InfinityFieldEntity>> INFINITY_FIELD = ENTITIES.register("lc_infinity_field",
            () -> EntityType.Builder.<InfinityFieldEntity>of(InfinityFieldEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSave().clientTrackingRange(8).updateInterval(1).build("lc_infinity_field"));

    /** Azul: ponto de atração que suga entidades e arranca blocos. */
    public static final DeferredHolder<EntityType<?>, EntityType<BlueOrbEntity>> BLUE_ORB = ENTITIES.register("lc_blue_orb",
            () -> EntityType.Builder.<BlueOrbEntity>of(BlueOrbEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSave().clientTrackingRange(10).updateInterval(2).build("lc_blue_orb"));

    /** Vermelho: ponto de repulsão disparado/colocado que explode numa onda vermelha. */
    public static final DeferredHolder<EntityType<?>, EntityType<RedOrbEntity>> RED_ORB = ENTITIES.register("lc_red_orb",
            () -> EntityType.Builder.<RedOrbEntity>of(RedOrbEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSave().clientTrackingRange(10).updateInterval(1).build("lc_red_orb"));

    /** Fuga: arco de chamas na mão → flecha flamejante → coluna de fogo. */
    public static final DeferredHolder<EntityType<?>, EntityType<FugaArrowEntity>> FUGA_ARROW = ENTITIES.register("lc_fuga_arrow",
            () -> EntityType.Builder.<FugaArrowEntity>of(FugaArrowEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSave().clientTrackingRange(12).updateInterval(1).build("lc_fuga_arrow"));

    /** Vazio Infinito: marcador do interior cósmico (o cliente desenha o cosmos para quem está dentro). */
    public static final DeferredHolder<EntityType<?>, EntityType<VoidFieldEntity>> VOID_FIELD = ENTITIES.register("lc_void_field",
            () -> EntityType.Builder.<VoidFieldEntity>of(VoidFieldEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSave().clientTrackingRange(16).updateInterval(20).build("lc_void_field"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
    }

    private LcRegistry() {}
}
