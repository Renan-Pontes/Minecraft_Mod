package com.renanpontes.isekaimahou.feature.class_enchanter;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.class_enchanter.effect.EnchantEffect;
import com.renanpontes.isekaimahou.feature.class_enchanter.effect.TransformEffect;
import com.renanpontes.isekaimahou.feature.class_enchanter.entity.SovereignSanctumEntity;
import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterArmorItem;
import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterCodexItem;
import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterGauntletsItem;
import com.renanpontes.isekaimahou.feature.class_enchanter.item.EnchanterOutfit;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registros do Encantador: os 9 encantamentos (efeitos), as Manoplas do Encantador (kit + tiers I–IV), o Códex de
 * Inscrição, o traje de 4 graus (GeckoLib) e o Santuário do Soberano (a forma G).
 */
public final class EnchRegistries {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);

    // ------------------------------------------------------------------ encantamentos (efeitos)
    public static final DeferredHolder<MobEffect, MobEffect> MIGHT = EFFECTS.register("enchant_might",
            () -> new EnchantEffect(EnchantEffect.Kind.MIGHT));
    public static final DeferredHolder<MobEffect, MobEffect> HASTE = EFFECTS.register("enchant_haste",
            () -> new EnchantEffect(EnchantEffect.Kind.HASTE));
    public static final DeferredHolder<MobEffect, MobEffect> AEGIS = EFFECTS.register("enchant_aegis",
            () -> new EnchantEffect(EnchantEffect.Kind.AEGIS));
    public static final DeferredHolder<MobEffect, MobEffect> VITALITY = EFFECTS.register("enchant_vitality",
            () -> new EnchantEffect(EnchantEffect.Kind.VITALITY));
    public static final DeferredHolder<MobEffect, MobEffect> IMBUE = EFFECTS.register("enchant_imbue",
            () -> new EnchantEffect(EnchantEffect.Kind.IMBUE));
    public static final DeferredHolder<MobEffect, MobEffect> OVERDRIVE = EFFECTS.register("enchant_overdrive",
            () -> new EnchantEffect(EnchantEffect.Kind.OVERDRIVE));
    public static final DeferredHolder<MobEffect, MobEffect> GEAR = EFFECTS.register("enchant_gear",
            () -> new EnchantEffect(EnchantEffect.Kind.GEAR));
    public static final DeferredHolder<MobEffect, MobEffect> ANTI_MAGIC = EFFECTS.register("anti_magic",
            () -> new EnchantEffect(EnchantEffect.Kind.ANTI_MAGIC));
    public static final DeferredHolder<MobEffect, MobEffect> HEAVENLY = EFFECTS.register("heavenly_restriction",
            () -> new EnchantEffect(EnchantEffect.Kind.HEAVENLY));

    // ------------------------------------------------------------------ transformações (técnicas de anime)
    public static final DeferredHolder<MobEffect, MobEffect> GATES = EFFECTS.register("enchanter_eight_gates",
            () -> new TransformEffect(TransformEffect.Kind.GATES));
    public static final DeferredHolder<MobEffect, MobEffect> KAIOKEN = EFFECTS.register("enchanter_kaioken",
            () -> new TransformEffect(TransformEffect.Kind.KAIOKEN));
    public static final DeferredHolder<MobEffect, MobEffect> GEAR_SECOND = EFFECTS.register("enchanter_gear_second",
            () -> new TransformEffect(TransformEffect.Kind.GEAR_SECOND));
    public static final DeferredHolder<MobEffect, MobEffect> GEAR_FOURTH = EFFECTS.register("enchanter_gear_fourth",
            () -> new TransformEffect(TransformEffect.Kind.GEAR_FOURTH));
    public static final DeferredHolder<MobEffect, MobEffect> ULTRA_INSTINCT = EFFECTS.register("enchanter_ultra_instinct",
            () -> new TransformEffect(TransformEffect.Kind.ULTRA_INSTINCT));

    /** As transformações (não contam no limite de encantamentos por alvo). */
    public static List<Holder<MobEffect>> transformations() {
        return List.of(GATES, KAIOKEN, GEAR_SECOND, GEAR_FOURTH, ULTRA_INSTINCT);
    }

    /** Todos os encantamentos, na ordem da aba (os dois de assinatura no fim). */
    public static List<Holder<MobEffect>> enchantments() {
        return List.of(MIGHT, HASTE, AEGIS, VITALITY, IMBUE, OVERDRIVE, GEAR, ANTI_MAGIC, HEAVENLY);
    }

    // ------------------------------------------------------------------ armas e itens
    /** Arma do kit (id EXATO esperado por {@code MagicPath.ENCHANTER.kitWeapon()}). */
    public static final DeferredItem<EnchanterGauntletsItem> GAUNTLETS = ITEMS.register("enchanter_gauntlets",
            () -> new EnchanterGauntletsItem(0, new Item.Properties().rarity(Rarity.COMMON)));
    /** Tiers I–IV ({@code enchanter_gauntlets_t1..t4}) para a Guilda/Provações ({@code ClassKits.tieredWeaponPrefix}). */
    public static final List<DeferredItem<EnchanterGauntletsItem>> GAUNTLET_TIERS = List.of(
            ITEMS.register("enchanter_gauntlets_t1", () -> new EnchanterGauntletsItem(1, new Item.Properties().rarity(Rarity.COMMON))),
            ITEMS.register("enchanter_gauntlets_t2", () -> new EnchanterGauntletsItem(2, new Item.Properties().rarity(Rarity.UNCOMMON))),
            ITEMS.register("enchanter_gauntlets_t3", () -> new EnchanterGauntletsItem(3, new Item.Properties().rarity(Rarity.RARE))),
            ITEMS.register("enchanter_gauntlets_t4", () -> new EnchanterGauntletsItem(4,
                    new Item.Properties().rarity(Rarity.EPIC).fireResistant())));

    public static final DeferredItem<EnchanterCodexItem> CODEX = ITEMS.register("enchanter_codex",
            () -> new EnchanterCodexItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    // ------------------------------------------------------------------ traje (4 graus × 4 peças)
    public static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
            ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
    private static final Map<EnchanterOutfit, DeferredHolder<ArmorMaterial, ArmorMaterial>> MATERIAL = new EnumMap<>(EnchanterOutfit.class);
    private static final Map<EnchanterOutfit, Map<ArmorItem.Type, DeferredItem<EnchanterArmorItem>>> PIECES =
            new EnumMap<>(EnchanterOutfit.class);

    static {
        for (EnchanterOutfit tier : EnchanterOutfit.values()) {
            DeferredHolder<ArmorMaterial, ArmorMaterial> mat = MATERIALS.register(tier.materialId(), () -> tier.buildMaterial(
                    equipSound(tier), repair(tier)));
            MATERIAL.put(tier, mat);
            Map<ArmorItem.Type, DeferredItem<EnchanterArmorItem>> pieces = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type type : TYPES) {
                pieces.put(type, ITEMS.register(tier.itemId(type), () -> new EnchanterArmorItem(tier, mat, type,
                        new Item.Properties().durability(type.getDurability(tier.durabilityFactor)).rarity(tier.rarity))));
            }
            PIECES.put(tier, pieces);
        }
    }

    private static Holder<SoundEvent> equipSound(EnchanterOutfit tier) {
        return switch (tier) {
            case NOVICE -> SoundEvents.ARMOR_EQUIP_LEATHER;
            case ADEPT -> SoundEvents.ARMOR_EQUIP_CHAIN;
            case MASTER -> SoundEvents.ARMOR_EQUIP_GOLD;
            case SOVEREIGN -> SoundEvents.ARMOR_EQUIP_NETHERITE;
        };
    }

    private static Supplier<Ingredient> repair(EnchanterOutfit tier) {
        return tier == EnchanterOutfit.NOVICE ? () -> Ingredient.of(net.minecraft.world.item.Items.LEATHER, net.minecraft.world.item.Items.LAPIS_LAZULI)
                : () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get());
    }

    public static DeferredItem<EnchanterArmorItem> piece(EnchanterOutfit tier, ArmorItem.Type type) {
        return PIECES.get(tier).get(type);
    }

    public static DeferredHolder<ArmorMaterial, ArmorMaterial> material(EnchanterOutfit tier) {
        return MATERIAL.get(tier);
    }

    // ------------------------------------------------------------------ entidade
    public static final DeferredHolder<EntityType<?>, EntityType<SovereignSanctumEntity>> SANCTUM = ENTITIES.register("enchanter_sanctum",
            () -> EntityType.Builder.<SovereignSanctumEntity>of(SovereignSanctumEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.4F).clientTrackingRange(10).updateInterval(10).noSave().fireImmune()
                    .build("enchanter_sanctum"));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        MATERIALS.register(modBus);
        ENTITIES.register(modBus);
    }

    private EnchRegistries() {}
}
