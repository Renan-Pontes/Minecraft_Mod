package com.renanpontes.isekaimahou.feature.forms_a;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.forms_a.entity.SpellBeamEntity;
import com.renanpontes.isekaimahou.feature.forms_a.entity.SpellFallingEntity;
import com.renanpontes.isekaimahou.feature.forms_a.entity.SpellWaveEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entidades das formas Onda e Queda, e o visual do Feixe. */
public final class FormsAEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SpellWaveEntity>> SPELL_WAVE = ENTITIES.register("spell_wave",
            () -> EntityType.Builder.<SpellWaveEntity>of(SpellWaveEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F).clientTrackingRange(8).updateInterval(1).fireImmune().build("spell_wave"));

    public static final DeferredHolder<EntityType<?>, EntityType<SpellFallingEntity>> SPELL_FALLING = ENTITIES.register("spell_falling",
            () -> EntityType.Builder.<SpellFallingEntity>of(SpellFallingEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F).clientTrackingRange(10).updateInterval(1).fireImmune().build("spell_falling"));

    /** Raio de luz do Feixe/Mira: só visual, parado, vive poucos ticks (como o relâmpago do vanilla: sem salvar, sem sync de movimento). */
    public static final DeferredHolder<EntityType<?>, EntityType<SpellBeamEntity>> SPELL_BEAM = ENTITIES.register("spell_beam",
            () -> EntityType.Builder.<SpellBeamEntity>of(SpellBeamEntity::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F).clientTrackingRange(10).updateInterval(Integer.MAX_VALUE)
                    .fireImmune().noSave().noSummon().build("spell_beam"));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    private FormsAEntities() {}
}
