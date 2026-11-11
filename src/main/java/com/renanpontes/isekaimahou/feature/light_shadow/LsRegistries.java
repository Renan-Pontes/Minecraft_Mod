package com.renanpontes.isekaimahou.feature.light_shadow;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.light_shadow.effect.DazzleEffect;
import com.renanpontes.isekaimahou.feature.light_shadow.effect.FearEffect;
import com.renanpontes.isekaimahou.feature.light_shadow.effect.NightmareEffect;
import com.renanpontes.isekaimahou.feature.light_shadow.effect.PainLinkEffect;
import com.renanpontes.isekaimahou.feature.light_shadow.effect.PossessionEffect;
import com.renanpontes.isekaimahou.feature.light_shadow.effect.ResurrectionGraceEffect;
import com.renanpontes.isekaimahou.feature.light_shadow.effect.ShackledEffect;
import com.renanpontes.isekaimahou.feature.light_shadow.entity.GuardianFairyEntity;
import com.renanpontes.isekaimahou.feature.light_shadow.entity.LivingShadowEntity;
import com.renanpontes.isekaimahou.feature.light_shadow.entity.SacredBarrierZoneEntity;
import com.renanpontes.isekaimahou.feature.light_shadow.entity.SanctuaryZoneEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo Luz/Sombra: efeitos de status e entidades (invocações e zonas). */
public final class LsRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    // ---------------- Efeitos de status ----------------
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);

    /** Ofuscado: mobs perdem o alvo e não conseguem escolher outro. */
    public static final DeferredHolder<MobEffect, MobEffect> HOLY_DAZZLE = EFFECTS.register("holy_dazzle", DazzleEffect::new);
    /** Amedrontado: mobs fogem da origem do medo; todos causam menos dano. */
    public static final DeferredHolder<MobEffect, MobEffect> SHADOW_FEAR = EFFECTS.register("shadow_fear",
            () -> new FearEffect().addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("effect.shadow_fear"),
                    -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    /** Acorrentado: preso a uma âncora por correntes sombrias. */
    public static final DeferredHolder<MobEffect, MobEffect> SHADOW_CHAINS = EFFECTS.register("shadow_chains", ShackledEffect::new);
    /** Vínculo de Dor: parte do dano recebido se espalha para os inimigos de quem conjurou. */
    public static final DeferredHolder<MobEffect, MobEffect> PAIN_LINK = EFFECTS.register("pain_link", PainLinkEffect::new);
    /** Pesadelo: sussurros, dano psíquico e pavor. */
    public static final DeferredHolder<MobEffect, MobEffect> NIGHTMARE_CURSE = EFFECTS.register("nightmare_curse", NightmareEffect::new);
    /** Possuído: o mob luta ao lado de quem o possuiu. */
    public static final DeferredHolder<MobEffect, MobEffect> SHADOW_POSSESSION = EFFECTS.register("shadow_possession", PossessionEffect::new);
    /** Graça da Ressurreição: totem temporário. */
    public static final DeferredHolder<MobEffect, MobEffect> RESURRECTION_GRACE = EFFECTS.register("resurrection_grace", ResurrectionGraceEffect::new);

    // ---------------- Entidades ----------------
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<GuardianFairyEntity>> GUARDIAN_FAIRY = ENTITIES.register("summoned_guardian_fairy",
            () -> EntityType.Builder.<GuardianFairyEntity>of(GuardianFairyEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.85F).eyeHeight(0.6F).clientTrackingRange(8).build("summoned_guardian_fairy"));
    public static final DeferredHolder<EntityType<?>, EntityType<LivingShadowEntity>> LIVING_SHADOW = ENTITIES.register("living_shadow",
            () -> EntityType.Builder.<LivingShadowEntity>of(LivingShadowEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).eyeHeight(1.62F).clientTrackingRange(8).build("living_shadow"));
    public static final DeferredHolder<EntityType<?>, EntityType<SacredBarrierZoneEntity>> SACRED_BARRIER_ZONE = ENTITIES.register("sacred_barrier_zone",
            () -> EntityType.Builder.<SacredBarrierZoneEntity>of(SacredBarrierZoneEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSummon().clientTrackingRange(10).updateInterval(40).build("sacred_barrier_zone"));
    public static final DeferredHolder<EntityType<?>, EntityType<SanctuaryZoneEntity>> SANCTUARY_ZONE = ENTITIES.register("sanctuary_zone",
            () -> EntityType.Builder.<SanctuaryZoneEntity>of(SanctuaryZoneEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).fireImmune().noSummon().clientTrackingRange(10).updateInterval(40).build("sanctuary_zone"));

    // ---------------- Ovos (criativo: invocações sem dono ficam no mundo) ----------------
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<DeferredSpawnEggItem> GUARDIAN_FAIRY_EGG = ITEMS.register("summoned_guardian_fairy_spawn_egg",
            () -> new DeferredSpawnEggItem(GUARDIAN_FAIRY, 0xFFF6D0, 0x9CDCFF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> LIVING_SHADOW_EGG = ITEMS.register("living_shadow_spawn_egg",
            () -> new DeferredSpawnEggItem(LIVING_SHADOW, 0x140A1E, 0x8A4FC8, new Item.Properties()));

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }

    private LsRegistries() {}
}
