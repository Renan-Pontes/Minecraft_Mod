package com.renanpontes.isekaimahou.feature.class_quincy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.LightCageEntity;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.QuincyNpcEntity;
import com.renanpontes.isekaimahou.feature.class_quincy.entity.SpiritArrowEntity;
import com.renanpontes.isekaimahou.feature.class_quincy.item.QuincyArmorItem;
import com.renanpontes.isekaimahou.feature.class_quincy.item.QuincyCrossItem;
import com.renanpontes.isekaimahou.feature.class_quincy.item.SanreiGloveItem;
import com.renanpontes.isekaimahou.feature.class_quincy.item.SeeleSchneiderItem;
import com.renanpontes.isekaimahou.feature.class_quincy.item.SilverTubeItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Itens, entidades e materiais de armadura da classe Quincy (ids prefixados com {@code quincy_}). */
public final class QuincyRegistries {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, IsekaiMahou.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SpiritArrowEntity>> SPIRIT_ARROW = ENTITIES.register("quincy_spirit_arrow",
            () -> EntityType.Builder.<SpiritArrowEntity>of(SpiritArrowEntity::new, MobCategory.MISC).sized(0.4F, 0.4F)
                    .eyeHeight(0.13F).clientTrackingRange(6).updateInterval(20).build("quincy_spirit_arrow"));
    public static final DeferredHolder<EntityType<?>, EntityType<QuincyNpcEntity>> QUINCY_NPC = ENTITIES.register("quincy_npc",
            () -> EntityType.Builder.of(QuincyNpcEntity::new, MobCategory.CREATURE).sized(0.6F, 1.95F).clientTrackingRange(10)
                    .build("quincy_npc"));
    /** Sankt Zwinger: a gaiola de luz (temporária, nunca salva). */
    public static final DeferredHolder<EntityType<?>, EntityType<LightCageEntity>> LIGHT_CAGE = ENTITIES.register("quincy_light_cage",
            () -> EntityType.Builder.<LightCageEntity>of(LightCageEntity::new, MobCategory.MISC).sized(1.0F, 1.0F).noSave()
                    .clientTrackingRange(8).updateInterval(20).build("quincy_light_cage"));

    /** Arma do kit: o id é EXATAMENTE o {@code kitWeapon} do MagicPath.QUINCY. */
    public static final DeferredItem<QuincyCrossItem> CROSS = ITEMS.register("quincy_cross",
            () -> new QuincyCrossItem(1, new Item.Properties().stacksTo(1)));
    /** Graus para a Guilda/Provações ({@code ClassKits.tieredWeaponPrefix} procura {@code quincy_cross_t1}). */
    public static final DeferredItem<QuincyCrossItem> CROSS_T1 = ITEMS.register("quincy_cross_t1",
            () -> new QuincyCrossItem(1, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<QuincyCrossItem> CROSS_T2 = ITEMS.register("quincy_cross_t2",
            () -> new QuincyCrossItem(2, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<QuincyCrossItem> CROSS_T3 = ITEMS.register("quincy_cross_t3",
            () -> new QuincyCrossItem(3, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<QuincyCrossItem> CROSS_T4 = ITEMS.register("quincy_cross_t4",
            () -> new QuincyCrossItem(4, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<SeeleSchneiderItem> SEELE_SCHNEIDER = ITEMS.register("quincy_seele_schneider",
            () -> new SeeleSchneiderItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<SilverTubeItem> SILVER_TUBE = ITEMS.register("quincy_silver_tube",
            () -> new SilverTubeItem(new Item.Properties().stacksTo(16)));
    /** Luva Sanrei: treino da Letzt Stil (técnicas mais fracas, abates contam). */
    public static final DeferredItem<SanreiGloveItem> SANREI_GLOVE = ITEMS.register("quincy_sanrei_glove",
            () -> new SanreiGloveItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<DeferredSpawnEggItem> NPC_EGG = ITEMS.register("quincy_npc_spawn_egg",
            () -> new DeferredSpawnEggItem(QUINCY_NPC, 0xEEF2F8, 0x3576D0, new Item.Properties()));

    static final Map<QuincyArmor.Tier, DeferredHolder<ArmorMaterial, ArmorMaterial>> MATERIAL = new EnumMap<>(QuincyArmor.Tier.class);

    static {
        for (QuincyArmor.Tier tier : QuincyArmor.Tier.values()) {
            DeferredHolder<ArmorMaterial, ArmorMaterial> material = MATERIALS.register(tier.modelName(), () -> material(tier));
            MATERIAL.put(tier, material);
            Map<ArmorItem.Type, DeferredItem<QuincyArmorItem>> pieces = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type type : QuincyArmor.TYPES) {
                pieces.put(type, ITEMS.register(tier.itemId(type), () -> new QuincyArmorItem(tier, material, type,
                        props(tier, type))));
            }
            QuincyArmor.PIECES.put(tier, pieces);
        }
    }

    private static ArmorMaterial material(QuincyArmor.Tier tier) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) defense.put(type, type == ArmorItem.Type.BODY ? 0 : tier.defense(type));
        var sound = switch (tier) {
            case APPRENTICE, ADEPT -> SoundEvents.ARMOR_EQUIP_LEATHER;
            case MASTER -> SoundEvents.ARMOR_EQUIP_GOLD;
            case ARCHMAGE -> SoundEvents.ARMOR_EQUIP_NETHERITE;
        };
        return new ArmorMaterial(defense, 20, sound,
                tier == QuincyArmor.Tier.APPRENTICE
                        ? () -> Ingredient.of(ItemTags.WOOL)
                        : () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get()),
                List.of(new ArmorMaterial.Layer(IsekaiMahou.id(tier.modelName()))), tier.toughness, 0.0F);
    }

    private static Item.Properties props(QuincyArmor.Tier tier, ArmorItem.Type type) {
        Item.Properties p = new Item.Properties().durability(type.getDurability(tier.durability)).rarity(tier.rarity);
        return tier == QuincyArmor.Tier.ARCHMAGE ? p.fireResistant() : p;
    }

    /** Itens na ordem da aba criativa. */
    public static List<ItemStack> creativeItems() {
        List<ItemStack> out = new java.util.ArrayList<>();
        out.add(new ItemStack(CROSS.get()));
        out.add(new ItemStack(CROSS_T2.get()));
        out.add(new ItemStack(CROSS_T3.get()));
        out.add(new ItemStack(CROSS_T4.get()));
        out.add(new ItemStack(SEELE_SCHNEIDER.get()));
        out.add(new ItemStack(SILVER_TUBE.get()));
        out.add(new ItemStack(SANREI_GLOVE.get()));
        for (QuincyArmor.Tier t : QuincyArmor.Tier.values()) {
            for (ArmorItem.Type type : QuincyArmor.TYPES) out.add(new ItemStack(QuincyArmor.piece(t, type).get()));
        }
        return out;
    }

    public static boolean isCross(ItemStack s) {
        return s.getItem() instanceof QuincyCrossItem;
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        MATERIALS.register(modBus);
    }

    static Item fallbackIcon() {
        return Items.SPECTRAL_ARROW;
    }

    private QuincyRegistries() {}
}
