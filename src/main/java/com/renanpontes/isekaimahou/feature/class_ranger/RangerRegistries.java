package com.renanpontes.isekaimahou.feature.class_ranger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.BulletLineEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.ChainTetherEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerEagleEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerSteedEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.RangerWolfEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.entity.SpiritEagleEntity;
import com.renanpontes.isekaimahou.feature.class_ranger.item.RangerArmorItem;
import com.renanpontes.isekaimahou.feature.class_ranger.item.RangerBowItem;
import com.renanpontes.isekaimahou.feature.class_ranger.item.RangerOutfitTier;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registros do módulo class_ranger: companheiros (Lobo, Águia, Corcel), a Águia Espiritual da forma G, o Arco do
 * Ranger (kit + graus t1..t4), o Traje Selvagem (4 graus × 4 peças) e os ovos.
 */
public final class RangerRegistries {
    private static final String MODID = IsekaiMahou.MODID;

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, MODID);

    // ---------------- Entidades ----------------
    public static final DeferredHolder<EntityType<?>, EntityType<RangerWolfEntity>> WOLF = ENTITIES.register(RangerWolfEntity.ID,
            () -> EntityType.Builder.<RangerWolfEntity>of(RangerWolfEntity::new, MobCategory.CREATURE)
                    .sized(0.8F, 0.95F).eyeHeight(0.8F).clientTrackingRange(10).build(RangerWolfEntity.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<RangerEagleEntity>> EAGLE = ENTITIES.register(RangerEagleEntity.ID,
            () -> EntityType.Builder.<RangerEagleEntity>of(RangerEagleEntity::new, MobCategory.CREATURE)
                    .sized(0.7F, 0.7F).eyeHeight(0.5F).clientTrackingRange(10).build(RangerEagleEntity.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<RangerSteedEntity>> STEED = ENTITIES.register(RangerSteedEntity.ID,
            () -> EntityType.Builder.<RangerSteedEntity>of(RangerSteedEntity::new, MobCategory.CREATURE)
                    .sized(1.3964844F, 1.6F).eyeHeight(1.52F).passengerAttachments(new Vec3(0.0, 1.42, 0.0))
                    .clientTrackingRange(10).build(RangerSteedEntity.ID));
    public static final DeferredHolder<EntityType<?>, EntityType<SpiritEagleEntity>> SPIRIT_EAGLE = ENTITIES.register(SpiritEagleEntity.ID,
            () -> EntityType.Builder.<SpiritEagleEntity>of(SpiritEagleEntity::new, MobCategory.MISC)
                    .sized(1.5F, 1.0F).clientTrackingRange(12).updateInterval(2).noSave().fireImmune().build(SpiritEagleEntity.ID));

    /** Corrente Conjurada (pacote de técnicas de anime): temporária, não salva. */
    public static final DeferredHolder<EntityType<?>, EntityType<ChainTetherEntity>> CHAIN_TETHER = ENTITIES.register(ChainTetherEntity.ID,
            () -> EntityType.Builder.<ChainTetherEntity>of(ChainTetherEntity::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F).clientTrackingRange(8).updateInterval(1).noSave().fireImmune().build(ChainTetherEntity.ID));
    /** Linha de Bala (mira + traçante): temporária, não salva. */
    public static final DeferredHolder<EntityType<?>, EntityType<BulletLineEntity>> BULLET_LINE = ENTITIES.register(BulletLineEntity.ID,
            () -> EntityType.Builder.<BulletLineEntity>of(BulletLineEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F).clientTrackingRange(10).updateInterval(1).noSave().fireImmune().build(BulletLineEntity.ID));

    // ---------------- Arcos ----------------
    /** Arma do kit (id exigido por MagicPath.RANGER.kitWeapon()). */
    public static final DeferredItem<RangerBowItem> BOW = ITEMS.register("ranger_bow",
            () -> new RangerBowItem(0, new Item.Properties().durability(420)));
    public static final List<DeferredItem<RangerBowItem>> BOW_TIERS = new ArrayList<>();

    static {
        int[] durability = {520, 760, 1100, 1700};
        Rarity[] rarity = {Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC};
        for (int t = 1; t <= 4; t++) {
            final int tier = t;
            BOW_TIERS.add(ITEMS.register("ranger_bow_t" + t, () -> {
                Item.Properties p = new Item.Properties().durability(durability[tier - 1]).rarity(rarity[tier - 1]);
                return new RangerBowItem(tier, tier == 4 ? p.fireResistant() : p);
            }));
        }
    }

    // ---------------- Traje Selvagem ----------------
    private static final Map<RangerOutfitTier, DeferredHolder<ArmorMaterial, ArmorMaterial>> MATERIAL = new EnumMap<>(RangerOutfitTier.class);
    private static final Map<RangerOutfitTier, Map<ArmorItem.Type, DeferredItem<RangerArmorItem>>> PIECES = new EnumMap<>(RangerOutfitTier.class);

    static {
        for (RangerOutfitTier tier : RangerOutfitTier.values()) {
            DeferredHolder<ArmorMaterial, ArmorMaterial> mat = MATERIALS.register(tier.modelId(), () -> material(tier));
            MATERIAL.put(tier, mat);
            Map<ArmorItem.Type, DeferredItem<RangerArmorItem>> pieces = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type type : RangerOutfitTier.TYPES) {
                pieces.put(type, ITEMS.register(tier.itemId(type), () -> {
                    Item.Properties p = new Item.Properties().durability(type.getDurability(tier.durabilityFactor)).rarity(tier.rarity);
                    return new RangerArmorItem(tier, mat, type, tier == RangerOutfitTier.BEASTLORD ? p.fireResistant() : p);
                }));
            }
            PIECES.put(tier, pieces);
        }
    }

    private static ArmorMaterial material(RangerOutfitTier tier) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) defense.put(type, type == ArmorItem.Type.BODY ? 0 : tier.defense(type));
        Holder<SoundEvent> sound = switch (tier) {
            case SCOUT, TRACKER -> SoundEvents.ARMOR_EQUIP_LEATHER;
            case WARDEN -> SoundEvents.ARMOR_EQUIP_CHAIN;
            case BEASTLORD -> SoundEvents.ARMOR_EQUIP_NETHERITE;
        };
        return new ArmorMaterial(defense, 20, sound,
                tier == RangerOutfitTier.SCOUT ? () -> Ingredient.of(Items.LEATHER) : () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get()),
                List.of(new ArmorMaterial.Layer(IsekaiMahou.id(tier.modelId()))), tier.toughness, 0.0F);
    }

    public static DeferredItem<RangerArmorItem> piece(RangerOutfitTier tier, ArmorItem.Type type) {
        return PIECES.get(tier).get(type);
    }

    // ---------------- Ovos ----------------
    public static final DeferredItem<DeferredSpawnEggItem> WOLF_EGG = ITEMS.register("ranger_wolf_spawn_egg",
            () -> new DeferredSpawnEggItem(WOLF, 0x6E6A64, 0x8FF08A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> EAGLE_EGG = ITEMS.register("ranger_eagle_spawn_egg",
            () -> new DeferredSpawnEggItem(EAGLE, 0x5A3A1E, 0xF2E6C8, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> STEED_EGG = ITEMS.register("ranger_steed_spawn_egg",
            () -> new DeferredSpawnEggItem(STEED, 0x7A4E2A, 0x6BCB5A, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        MATERIALS.register(modBus);
    }

    private RangerRegistries() {}
}
