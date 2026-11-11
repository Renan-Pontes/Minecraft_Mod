package com.renanpontes.isekaimahou.feature.arcane;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.arcane.block.ArcaneLightBlock;
import com.renanpontes.isekaimahou.feature.arcane.block.ConjuredStoneBlock;
import com.renanpontes.isekaimahou.feature.arcane.effect.ArcaneStatusEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo Arcano: blocos temporários (luz e pedra conjurada) e efeitos de status próprios. */
public final class ArcaneRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    // ---------------- Blocos (técnicos: sem item, somem sozinhos) ----------------
    /** Orbe de luz mágica (glifo Luz): sem colisão, substituível, some depois de um tempo. */
    public static final DeferredBlock<ArcaneLightBlock> ARCANE_LIGHT = BLOCKS.register("arcane_light",
            () -> new ArcaneLightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .replaceable()
                    .noCollission()
                    .instabreak()
                    .noLootTable()
                    .noOcclusion()
                    .lightLevel(s -> 15)
                    .sound(SoundType.AMETHYST_CLUSTER)
                    .pushReaction(PushReaction.DESTROY)
                    .isValidSpawn((s, l, p, t) -> false)
                    .isRedstoneConductor((s, l, p) -> false)
                    .isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false)));

    /** Pedra conjurada (glifo Colocar Bloco sem bloco na mão secundária): desmorona depois de um tempo. */
    public static final DeferredBlock<ConjuredStoneBlock> CONJURED_STONE = BLOCKS.register("conjured_stone",
            () -> new ConjuredStoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(1.0F, 6.0F)
                    .sound(SoundType.AMETHYST)
                    .lightLevel(s -> 4)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((s, l, p, t) -> false)));

    // ---------------- Efeitos de status ----------------
    /** Reflexão Arcana: projéteis que acertam voltam para quem os lançou (ver ArcaneEvents). */
    public static final DeferredHolder<MobEffect, MobEffect> REFLECT = EFFECTS.register("arcane_reflect",
            () -> new ArcaneStatusEffect(MobEffectCategory.BENEFICIAL, 0xC9A6FF, ArcaneStatusEffect.Aura.REFLECT));

    /** Encolhido: menor e mais fraco (cada nível = -30% de tamanho). */
    public static final DeferredHolder<MobEffect, MobEffect> SHRINK = EFFECTS.register("arcane_shrink",
            () -> new ArcaneStatusEffect(MobEffectCategory.HARMFUL, 0x7FD6E8, ArcaneStatusEffect.Aura.SHRINK)
                    .addAttributeModifier(Attributes.SCALE, IsekaiMahou.id("effect.arcane_shrink"), -0.3,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("effect.arcane_shrink"), -0.2,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.arcane_shrink"), -0.2,
                            AttributeModifier.Operation.ADD_VALUE));

    /** Agigantado: maior, mais forte, mais alcance (cada nível = +40% de tamanho). */
    public static final DeferredHolder<MobEffect, MobEffect> GROWTH = EFFECTS.register("arcane_growth",
            () -> new ArcaneStatusEffect(MobEffectCategory.BENEFICIAL, 0xFFB45A, ArcaneStatusEffect.Aura.GROWTH)
                    .addAttributeModifier(Attributes.SCALE, IsekaiMahou.id("effect.arcane_growth"), 0.4,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("effect.arcane_growth"), 0.2,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.arcane_growth"), 0.25,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.STEP_HEIGHT, IsekaiMahou.id("effect.arcane_growth"), 0.5,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ENTITY_INTERACTION_RANGE, IsekaiMahou.id("effect.arcane_growth"), 0.75,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.BLOCK_INTERACTION_RANGE, IsekaiMahou.id("effect.arcane_growth"), 0.75,
                            AttributeModifier.Operation.ADD_VALUE));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        EFFECTS.register(modBus);
    }

    private ArcaneRegistries() {}
}
