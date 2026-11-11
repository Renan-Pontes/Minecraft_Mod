package com.renanpontes.isekaimahou.feature.legends_f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.legends_f.effect.LfMobEffect;
import com.renanpontes.isekaimahou.feature.legends_f.entity.BarrierEntity;
import com.renanpontes.isekaimahou.feature.legends_f.entity.BroomEntity;
import com.renanpontes.isekaimahou.feature.legends_f.entity.EnvyHandsEntity;
import com.renanpontes.isekaimahou.feature.legends_f.entity.ForestGodEntity;
import com.renanpontes.isekaimahou.feature.legends_f.entity.KodamaEntity;
import com.renanpontes.isekaimahou.feature.legends_f.entity.ShadowSoldierEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registros do legends_f: entidades das técnicas (todas TEMPORÁRIAS, tipo {@code noSave}), efeitos de status das
 * transformações e o anexo do Exército das Sombras (permanente, sobrevive à morte).
 */
public final class LfRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);

    // ---------------------------------------------------------------- anexo
    /** Exército das Sombras: lista permanente de soldados (ver {@code necro.ShadowArmy}). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CompoundTag>> ARMY = ATTACHMENTS.register("shadow_army",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    // ---------------------------------------------------------------- entidades
    public static final DeferredHolder<EntityType<?>, EntityType<ShadowSoldierEntity>> SHADOW_SOLDIER = ENTITIES.register("shadow_soldier",
            () -> EntityType.Builder.of(ShadowSoldierEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).noSave().clientTrackingRange(10).build("shadow_soldier"));

    public static final DeferredHolder<EntityType<?>, EntityType<KodamaEntity>> KODAMA = ENTITIES.register("kodama",
            () -> EntityType.Builder.of(KodamaEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.75F).noSave().clientTrackingRange(8).build("kodama"));

    public static final DeferredHolder<EntityType<?>, EntityType<BroomEntity>> BROOM = ENTITIES.register("witch_broom",
            () -> EntityType.Builder.<BroomEntity>of(BroomEntity::new, MobCategory.MISC)
                    .sized(1.1F, 0.45F).passengerAttachments(0.35F).noSave().fireImmune()
                    .clientTrackingRange(10).updateInterval(1).build("witch_broom"));

    public static final DeferredHolder<EntityType<?>, EntityType<BarrierEntity>> BARRIER = ENTITIES.register("general_barrier",
            () -> EntityType.Builder.<BarrierEntity>of(BarrierEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).noSave().fireImmune().clientTrackingRange(10).updateInterval(5).build("general_barrier"));

    public static final DeferredHolder<EntityType<?>, EntityType<EnvyHandsEntity>> ENVY_HANDS = ENTITIES.register("envy_hands",
            () -> EntityType.Builder.<EnvyHandsEntity>of(EnvyHandsEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).noSave().fireImmune().clientTrackingRange(10).updateInterval(2).build("envy_hands"));

    public static final DeferredHolder<EntityType<?>, EntityType<ForestGodEntity>> FOREST_GOD = ENTITIES.register("forest_god",
            () -> EntityType.Builder.<ForestGodEntity>of(ForestGodEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).noSave().fireImmune().clientTrackingRange(12).updateInterval(2).build("forest_god"));

    // ---------------------------------------------------------------- efeitos
    /** Mana Oculta (Mago): de longe ninguém te nota; o primeiro golpe mágico sai ×1,6. */
    public static final DeferredHolder<MobEffect, MobEffect> MANA_CONCEALED = EFFECTS.register("mana_concealed",
            () -> new LfMobEffect(MobEffectCategory.BENEFICIAL, 0x7FD8E8, false));

    /** Sono Encantado (Bruxa): parado, sem atacar; o primeiro dano acorda (e sai ×1,5). */
    public static final DeferredHolder<MobEffect, MobEffect> ENCHANTED_SLEEP = EFFECTS.register("enchanted_sleep",
            () -> new LfMobEffect(MobEffectCategory.HARMFUL, 0xF0A0D0, true)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.enchanted_sleep"), -0.9,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** Demônio da Motosserra (Bruxo). */
    public static final DeferredHolder<MobEffect, MobEffect> CHAINSAW_DEVIL = EFFECTS.register("chainsaw_devil",
            () -> new LfMobEffect(MobEffectCategory.BENEFICIAL, 0xFF7A1A, false)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("effect.chainsaw_devil"), 7.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("effect.chainsaw_devil"), 0.5,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.chainsaw_devil"), 0.2,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id("effect.chainsaw_devil"), 4.0,
                            AttributeModifier.Operation.ADD_VALUE));

    /** Contrato Faustiano (Bruxo): poder em troca de vida máxima, até cumprir ou não o contrato. */
    public static final DeferredHolder<MobEffect, MobEffect> FAUST_CONTRACT = EFFECTS.register("faust_contract",
            () -> new LfMobEffect(MobEffectCategory.NEUTRAL, 0xC0182E, false)
                    .addAttributeModifier(Attributes.MAX_HEALTH, IsekaiMahou.id("effect.faust_contract"), -0.3,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(CoreRegistries.SPELL_POWER, IsekaiMahou.id("effect.faust_contract"), 0.4,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(CoreRegistries.MANA_REGEN, IsekaiMahou.id("effect.faust_contract"), 1.0,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** Modo Sábio (Druida). */
    public static final DeferredHolder<MobEffect, MobEffect> SAGE_MODE = EFFECTS.register("sage_mode",
            () -> new LfMobEffect(MobEffectCategory.BENEFICIAL, 0xF08A24, false)
                    .addAttributeModifier(CoreRegistries.SPELL_POWER, IsekaiMahou.id("effect.sage_mode"), 0.35,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("effect.sage_mode"), 4.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.sage_mode"), 0.15,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id("effect.sage_mode"), 4.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(CoreRegistries.MANA_REGEN, IsekaiMahou.id("effect.sage_mode"), 1.0,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    /** Monarca das Sombras (Necromante). */
    public static final DeferredHolder<MobEffect, MobEffect> SHADOW_MONARCH = EFFECTS.register("shadow_monarch",
            () -> new LfMobEffect(MobEffectCategory.BENEFICIAL, 0x6A3DD8, false)
                    .addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id("effect.shadow_monarch"), 6.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("effect.shadow_monarch"), 5.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.shadow_monarch"), 0.15,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.shadow_monarch"), 0.5,
                            AttributeModifier.Operation.ADD_VALUE));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(LfRegistry::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(SHADOW_SOLDIER.get(), ShadowSoldierEntity.createAttributes().build());
        e.put(KODAMA.get(), KodamaEntity.createAttributes().build());
        e.put(BROOM.get(), LivingEntity.createLivingAttributes().build());
    }

    private LfRegistry() {}
}
