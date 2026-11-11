package com.renanpontes.isekaimahou.feature.origins_b;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.origins_b.effect.BoneShieldEffect;
import com.renanpontes.isekaimahou.feature.origins_b.effect.DeathContractEffect;
import com.renanpontes.isekaimahou.feature.origins_b.effect.DemonWingsEffect;
import com.renanpontes.isekaimahou.feature.origins_b.effect.LichFormEffect;
import com.renanpontes.isekaimahou.feature.origins_b.entity.BoneWarriorEntity;
import com.renanpontes.isekaimahou.feature.origins_b.entity.ImpFireballEntity;
import com.renanpontes.isekaimahou.feature.origins_b.entity.SummonedDemonEntity;
import com.renanpontes.isekaimahou.feature.origins_b.entity.SummonedImpEntity;
import com.renanpontes.isekaimahou.feature.origins_b.entity.UndeadServantEntity;
import com.renanpontes.isekaimahou.feature.origins_b.item.ShadowBladeItem;
import com.renanpontes.isekaimahou.feature.origins_b.item.ShadowBladeTimer;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.Unbreakable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo origins_b: invocações do Pacto e da Necromancia, a Lâmina Sombria e os efeitos de status. */
public final class ObRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    // ---------------- Entidades ----------------
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SummonedImpEntity>> SUMMONED_IMP = ENTITIES.register("summoned_imp",
            () -> EntityType.Builder.<SummonedImpEntity>of(SummonedImpEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.0F).eyeHeight(0.75F).fireImmune().clientTrackingRange(8).build("summoned_imp"));
    public static final DeferredHolder<EntityType<?>, EntityType<SummonedDemonEntity>> SUMMONED_DEMON = ENTITIES.register("summoned_demon",
            () -> EntityType.Builder.<SummonedDemonEntity>of(SummonedDemonEntity::new, MobCategory.MISC)
                    .sized(1.4F, 3.0F).eyeHeight(2.6F).fireImmune().clientTrackingRange(10).build("summoned_demon"));
    public static final DeferredHolder<EntityType<?>, EntityType<UndeadServantEntity>> UNDEAD_SERVANT = ENTITIES.register("undead_servant",
            () -> EntityType.Builder.<UndeadServantEntity>of(UndeadServantEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).eyeHeight(1.74F).clientTrackingRange(8).build("undead_servant"));
    public static final DeferredHolder<EntityType<?>, EntityType<BoneWarriorEntity>> BONE_WARRIOR = ENTITIES.register("bone_warrior",
            () -> EntityType.Builder.<BoneWarriorEntity>of(BoneWarriorEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.99F).eyeHeight(1.74F).clientTrackingRange(8).build("bone_warrior"));
    public static final DeferredHolder<EntityType<?>, EntityType<ImpFireballEntity>> IMP_FIREBALL = ENTITIES.register("imp_fireball",
            () -> EntityType.Builder.<ImpFireballEntity>of(ImpFireballEntity::new, MobCategory.MISC)
                    .sized(0.3125F, 0.3125F).noSummon().clientTrackingRange(4).updateInterval(10).build("imp_fireball"));

    // ---------------- Efeitos de status ----------------
    /** Ids dos modificadores de voo (para limpar modificadores órfãos, ver ObEvents). */
    public static final ResourceLocation WINGS_FLIGHT_ID = IsekaiMahou.id("effect.demon_wings");
    public static final ResourceLocation LICH_FLIGHT_ID = IsekaiMahou.id("effect.lich_form.flight");

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);

    /** Escudo de Ossos: absorção + devolve parte do dano corpo a corpo. */
    public static final DeferredHolder<MobEffect, MobEffect> BONE_SHIELD = EFFECTS.register("bone_shield",
            () -> new BoneShieldEffect().addAttributeModifier(Attributes.MAX_ABSORPTION, IsekaiMahou.id("effect.bone_shield"),
                    BoneShieldEffect.ABSORPTION_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE));
    /** Contrato de Morte: marca; se o alvo morrer com ela, quem marcou ganha vida e mana. */
    public static final DeferredHolder<MobEffect, MobEffect> DEATH_CONTRACT = EFFECTS.register("death_contract", DeathContractEffect::new);
    /** Asas Demoníacas: voo temporário (atributo de voo do NeoForge, que some sozinho com o efeito). */
    public static final DeferredHolder<MobEffect, MobEffect> DEMON_WINGS = EFFECTS.register("demon_wings",
            () -> new DemonWingsEffect().addAttributeModifier(NeoForgeMod.CREATIVE_FLIGHT, WINGS_FLIGHT_ID,
                    1.0, AttributeModifier.Operation.ADD_VALUE));
    /** Forma de Lich: voo lento, armadura, resistência a recuo; golpes drenam vida. */
    public static final DeferredHolder<MobEffect, MobEffect> LICH_FORM = EFFECTS.register("lich_form",
            () -> new LichFormEffect()
                    .addAttributeModifier(NeoForgeMod.CREATIVE_FLIGHT, LICH_FLIGHT_ID, 1.0, AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id("effect.lich_form.armor"), 6.0, AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ARMOR_TOUGHNESS, IsekaiMahou.id("effect.lich_form.toughness"), 3.0, AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.lich_form.knockback"), 0.5, AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.SAFE_FALL_DISTANCE, IsekaiMahou.id("effect.lich_form.fall"), 12.0, AttributeModifier.Operation.ADD_VALUE));

    // ---------------- Componentes ----------------
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    /** Tempo restante da Lâmina Sombria (em ticks) e o total, para a barra. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ShadowBladeTimer>> SHADOW_BLADE_TIMER =
            COMPONENTS.registerComponentType("shadow_blade_timer",
                    b -> b.persistent(ShadowBladeTimer.CODEC).networkSynchronized(ShadowBladeTimer.STREAM_CODEC));

    // ---------------- Itens ----------------
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<ShadowBladeItem> SHADOW_BLADE = ITEMS.register("shadow_blade",
            () -> new ShadowBladeItem(new Item.Properties()
                    .rarity(Rarity.EPIC)
                    .fireResistant()
                    .component(DataComponents.UNBREAKABLE, new Unbreakable(false))
                    .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 3, -2.2F))));

    // Ovos (criativo): invocações sem dono ficam no mundo e caçam monstros
    public static final DeferredItem<DeferredSpawnEggItem> SUMMONED_IMP_EGG = ITEMS.register("summoned_imp_spawn_egg",
            () -> new DeferredSpawnEggItem(SUMMONED_IMP, 0x8A1C22, 0xFFC93C, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SUMMONED_DEMON_EGG = ITEMS.register("summoned_demon_spawn_egg",
            () -> new DeferredSpawnEggItem(SUMMONED_DEMON, 0x2A0E10, 0xFF6A1A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> UNDEAD_SERVANT_EGG = ITEMS.register("undead_servant_spawn_egg",
            () -> new DeferredSpawnEggItem(UNDEAD_SERVANT, 0x4E5B4A, 0x3FE0D0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BONE_WARRIOR_EGG = ITEMS.register("bone_warrior_spawn_egg",
            () -> new DeferredSpawnEggItem(BONE_WARRIOR, 0xD8D2BE, 0x2F3A3A, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
    }

    private ObRegistries() {}
}
