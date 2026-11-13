package com.renanpontes.isekaimahou.feature.legends_g;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.legends_g.effect.LgMobEffect;
import com.renanpontes.isekaimahou.feature.legends_g.entity.GrimoireEntity;
import com.renanpontes.isekaimahou.feature.legends_g.entity.StoneShellEntity;

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

/**
 * Registros do legends_g: as duas entidades (Grim, o grimório-demônio, e a bala do Canhão de Pedra — TEMPORÁRIAS,
 * tipo {@code noSave}) e o efeito de status da Forma Negra.
 */
public final class LgRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);

    // ---------------------------------------------------------------- entidades
    /** Grim: o grimório-demônio que flutua no ombro, atira balas de mana e devora magias inimigas. */
    public static final DeferredHolder<EntityType<?>, EntityType<GrimoireEntity>> GRIMOIRE = ENTITIES.register("lg_grimoire",
            () -> EntityType.Builder.<GrimoireEntity>of(GrimoireEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.4F).noSave().fireImmune().clientTrackingRange(10).updateInterval(1).build("lg_grimoire"));

    /** Bala de pedra girando do Canhão de Pedra. */
    public static final DeferredHolder<EntityType<?>, EntityType<StoneShellEntity>> STONE_SHELL = ENTITIES.register("lg_stone_shell",
            () -> EntityType.Builder.<StoneShellEntity>of(StoneShellEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F).noSave().fireImmune().clientTrackingRange(10).updateInterval(1).build("lg_stone_shell"));

    // ---------------------------------------------------------------- efeitos
    /** Forma Negra (Asta): força, velocidade e pele que bebe magia (regras em {@code LgEvents}). */
    public static final DeferredHolder<MobEffect, MobEffect> BLACK_FORM = EFFECTS.register("lg_black_form",
            () -> new LgMobEffect(MobEffectCategory.BENEFICIAL, 0x1E1E1E)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("effect.lg_black_form"), 4.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("effect.lg_black_form"), 0.15,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.lg_black_form"), 0.2,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id("effect.lg_black_form"), 3.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.lg_black_form"), 0.3,
                            AttributeModifier.Operation.ADD_VALUE));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
    }

    private LgRegistry() {}
}
