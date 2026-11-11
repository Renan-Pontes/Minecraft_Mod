package com.renanpontes.isekaimahou.feature.legends_d;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_d.effect.AtomicBubbleEffect;
import com.renanpontes.isekaimahou.feature.legends_d.effect.GoatCurseEffect;
import com.renanpontes.isekaimahou.feature.legends_d.effect.TimeStoppedEffect;
import com.renanpontes.isekaimahou.feature.legends_d.entity.AtomicNovaEntity;
import com.renanpontes.isekaimahou.feature.legends_d.entity.DisintegrationCageEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo legends_d: efeitos Tempo Parado / Maldição da Cabra e as entidades visuais das lendas. */
public final class LdRegistry {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);

    /**
     * Tempo Parado (jogadores dentro de "Parar o Tempo"): sem andar, pular, voar, cair, alcançar blocos ou criaturas.
     * Ataque/uso/conjuração também são cancelados por eventos ({@code TimeStop}). Some sozinho se não houver parada ativa.
     */
    public static final DeferredHolder<MobEffect, MobEffect> TIME_STOPPED = EFFECTS.register("time_stopped",
            () -> new TimeStoppedEffect(MobEffectCategory.HARMFUL, 0xB8B8C8)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.time_stopped"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.FLYING_SPEED, IsekaiMahou.id("effect.time_stopped"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id("effect.time_stopped"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.GRAVITY, IsekaiMahou.id("effect.time_stopped"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("effect.time_stopped"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ENTITY_INTERACTION_RANGE, IsekaiMahou.id("effect.time_stopped"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.BLOCK_INTERACTION_RANGE, IsekaiMahou.id("effect.time_stopped"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** Maldição da Cabra (jogadores): não ataca nem conjura, pula mais alto, berra — e os outros o veem como cabra. */
    public static final DeferredHolder<MobEffect, MobEffect> GOAT_CURSE = EFFECTS.register("goat_curse",
            () -> new GoatCurseEffect(MobEffectCategory.HARMFUL, 0xD9CBB0)
                    .addAttributeModifier(Attributes.ENTITY_INTERACTION_RANGE, IsekaiMahou.id("effect.goat_curse"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id("effect.goat_curse"), 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.goat_curse"), 0.1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));

    /**
     * Bolha Protetora do Atômico (aliados do conjurador durante a explosão): sem gravidade (flutua se o chão sumir),
     * sem pulo e sem empurrão. O dano é cancelado pelo {@code AtomicBubble}.
     */
    public static final DeferredHolder<MobEffect, MobEffect> ATOMIC_BUBBLE = EFFECTS.register("atomic_bubble",
            () -> new AtomicBubbleEffect(MobEffectCategory.BENEFICIAL, 0x5AB8FF)
                    .addAttributeModifier(Attributes.GRAVITY, IsekaiMahou.id("effect.atomic_bubble"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id("effect.atomic_bubble"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.atomic_bubble"), 1.0, AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.atomic_bubble"), 1.0, AttributeModifier.Operation.ADD_VALUE));

    /** Eu Sou Atômico: anéis na carga e a esfera azul-violeta que se expande. Não é salva. */
    public static final DeferredHolder<EntityType<?>, EntityType<AtomicNovaEntity>> ATOMIC_NOVA = ENTITIES.register("atomic_nova",
            () -> EntityType.Builder.<AtomicNovaEntity>of(AtomicNovaEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSave().noSummon()
                    .clientTrackingRange(14).updateInterval(2).build("atomic_nova"));

    /** Desintegração: o cilindro de círculos sagrados e o pilar de luz. Não é salva. */
    public static final DeferredHolder<EntityType<?>, EntityType<DisintegrationCageEntity>> DISINTEGRATION_CAGE = ENTITIES.register("disintegration_cage",
            () -> EntityType.Builder.<DisintegrationCageEntity>of(DisintegrationCageEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSave().noSummon()
                    .clientTrackingRange(10).updateInterval(10).build("disintegration_cage"));

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
        ENTITIES.register(modBus);
    }

    private LdRegistry() {}
}
