package com.renanpontes.isekaimahou.feature.class_steelball;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.BallBreakerEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.InfiniteSpinEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.LoveTrainEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.ScanBallEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SpiralHoleEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.TuskNailEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SpinNailEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SpinVortexEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SteelBallEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.SteelballMentorEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.item.CorpsePartItem;
import com.renanpontes.isekaimahou.feature.class_steelball.item.HorseWhistleItem;
import com.renanpontes.isekaimahou.feature.class_steelball.item.MentorLetterItem;
import com.renanpontes.isekaimahou.feature.class_steelball.item.RelicItem;
import com.renanpontes.isekaimahou.feature.class_steelball.item.SaintCompassItem;
import com.renanpontes.isekaimahou.feature.class_steelball.item.SbOutfitItem;
import com.renanpontes.isekaimahou.feature.class_steelball.item.SbOutfitTier;
import com.renanpontes.isekaimahou.feature.class_steelball.item.SteelBallItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do Steel Ball: bolas, Partes do Cadáver, itens do Mestre, roupa de 4 graus, entidades e o efeito Girado. */
public final class SbRegistry {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, IsekaiMahou.MODID);

    // ---------------- Efeito ----------------
    public static final DeferredHolder<MobEffect, MobEffect> SPUN = EFFECTS.register("steelball_spun", SpunEffect::new);

    // ---------------- Bolas de aço ----------------
    /** Arma do kit: id EXATO do MagicPath ({@code steel_ball}). */
    public static final DeferredItem<SteelBallItem> STEEL_BALL = ITEMS.register("steel_ball", () -> new SteelBallItem(0, new Item.Properties()));
    /** Graus para a Guilda/Provações ({@code steel_ball_t1..t4}). */
    public static final List<DeferredItem<SteelBallItem>> BALL_TIERS = List.of(
            ITEMS.register("steel_ball_t1", () -> new SteelBallItem(1, new Item.Properties())),
            ITEMS.register("steel_ball_t2", () -> new SteelBallItem(2, new Item.Properties())),
            ITEMS.register("steel_ball_t3", () -> new SteelBallItem(3, new Item.Properties())),
            ITEMS.register("steel_ball_t4", () -> new SteelBallItem(4, new Item.Properties().fireResistant())));

    // ---------------- Cadáver Santo ----------------
    public static final Map<SbPart, DeferredItem<CorpsePartItem>> PARTS = new EnumMap<>(SbPart.class);
    public static final DeferredItem<RelicItem> RELIC = ITEMS.register("steelball_relic",
            () -> new RelicItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    static {
        for (SbPart p : SbPart.values()) {
            PARTS.put(p, ITEMS.register(p.itemId(), () -> new CorpsePartItem(p, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant())));
        }
    }

    // ---------------- Itens do Mestre ----------------
    public static final DeferredItem<SaintCompassItem> SAINT_COMPASS = ITEMS.register("steelball_saint_compass",
            () -> new SaintCompassItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<MentorLetterItem> MENTOR_LETTER = ITEMS.register("steelball_mentor_letter",
            () -> new MentorLetterItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<HorseWhistleItem> HORSE_WHISTLE = ITEMS.register("steelball_horse_whistle",
            () -> new HorseWhistleItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    // ---------------- Roupa (4 graus × 4 peças) ----------------
    private static final Map<SbOutfitTier, DeferredHolder<ArmorMaterial, ArmorMaterial>> MATERIAL = new EnumMap<>(SbOutfitTier.class);
    private static final Map<SbOutfitTier, Map<ArmorItem.Type, DeferredItem<SbOutfitItem>>> OUTFIT = new EnumMap<>(SbOutfitTier.class);

    static {
        for (SbOutfitTier tier : SbOutfitTier.values()) {
            DeferredHolder<ArmorMaterial, ArmorMaterial> mat = MATERIALS.register(tier.materialId(), () -> material(tier));
            MATERIAL.put(tier, mat);
            Map<ArmorItem.Type, DeferredItem<SbOutfitItem>> pieces = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type type : SbOutfitTier.TYPES) {
                pieces.put(type, ITEMS.register(tier.itemId(type), () -> new SbOutfitItem(tier, mat, type, outfitProps(tier, type))));
            }
            OUTFIT.put(tier, pieces);
        }
    }

    private static ArmorMaterial material(SbOutfitTier tier) {
        Map<ArmorItem.Type, Integer> def = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type t : ArmorItem.Type.values()) def.put(t, tier.defense(t));
        Supplier<Ingredient> repair = tier == SbOutfitTier.JOCKEY ? () -> Ingredient.of(Items.LEATHER)
                : () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get());
        var sound = switch (tier) {
            case JOCKEY, PLAINS -> SoundEvents.ARMOR_EQUIP_LEATHER;
            case ROTATION -> SoundEvents.ARMOR_EQUIP_CHAIN;
            case SAINT -> SoundEvents.ARMOR_EQUIP_GOLD;
        };
        return new ArmorMaterial(def, 20, sound, repair, List.of(new ArmorMaterial.Layer(IsekaiMahou.id(tier.materialId()))),
                tier.toughness, 0.0F);
    }

    private static Item.Properties outfitProps(SbOutfitTier tier, ArmorItem.Type type) {
        Item.Properties p = new Item.Properties().durability(type.getDurability(tier.durability)).rarity(tier.rarity);
        return tier == SbOutfitTier.SAINT ? p.fireResistant() : p;
    }

    public static DeferredItem<SbOutfitItem> outfit(SbOutfitTier tier, ArmorItem.Type type) {
        return OUTFIT.get(tier).get(type);
    }

    // ---------------- Entidades ----------------
    public static final Supplier<EntityType<SteelBallEntity>> BALL = ENTITIES.register("steelball_ball",
            () -> EntityType.Builder.<SteelBallEntity>of(SteelBallEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F).clientTrackingRange(6).updateInterval(1).build("steelball_ball"));
    public static final Supplier<EntityType<SpinNailEntity>> NAIL = ENTITIES.register("steelball_nail",
            () -> EntityType.Builder.<SpinNailEntity>of(SpinNailEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(1).build("steelball_nail"));
    public static final Supplier<EntityType<SpinVortexEntity>> VORTEX = ENTITIES.register("steelball_vortex",
            () -> EntityType.Builder.<SpinVortexEntity>of(SpinVortexEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.4F).clientTrackingRange(8).updateInterval(2).fireImmune().build("steelball_vortex"));
    public static final Supplier<EntityType<InfiniteSpinEntity>> INFINITE = ENTITIES.register("steelball_infinite_spin",
            () -> EntityType.Builder.<InfiniteSpinEntity>of(InfiniteSpinEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(8).updateInterval(1).fireImmune().build("steelball_infinite_spin"));
    /** v1.0 — Técnicas do Santo: unhas de Tusk, buraco espiral, Esfera Quebrantadora e Bola Escâner. */
    public static final Supplier<EntityType<TuskNailEntity>> TUSK_NAIL = ENTITIES.register("steelball_tusk_nail",
            () -> EntityType.Builder.<TuskNailEntity>of(TuskNailEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(1).build("steelball_tusk_nail"));
    public static final Supplier<EntityType<SpiralHoleEntity>> SPIRAL_HOLE = ENTITIES.register("steelball_spiral_hole",
            () -> EntityType.Builder.<SpiralHoleEntity>of(SpiralHoleEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.1F).clientTrackingRange(8).updateInterval(1).fireImmune().build("steelball_spiral_hole"));
    public static final Supplier<EntityType<BallBreakerEntity>> BALL_BREAKER = ENTITIES.register("steelball_ball_breaker",
            () -> EntityType.Builder.<BallBreakerEntity>of(BallBreakerEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(8).updateInterval(1).fireImmune().build("steelball_ball_breaker"));
    public static final Supplier<EntityType<ScanBallEntity>> SCAN_BALL = ENTITIES.register("steelball_scan_ball",
            () -> EntityType.Builder.<ScanBallEntity>of(ScanBallEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F).clientTrackingRange(8).updateInterval(1).build("steelball_scan_ball"));
    /** LOVE TRAIN: a cortina de luz que acompanha o portador (só visual + âncora; não salva). */
    public static final Supplier<EntityType<LoveTrainEntity>> LOVE_TRAIN = ENTITIES.register("steelball_love_train",
            () -> EntityType.Builder.<LoveTrainEntity>of(LoveTrainEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).updateInterval(1).fireImmune().noSave().noSummon()
                    .build("steelball_love_train"));
    public static final Supplier<EntityType<SteelballMentorEntity>> MENTOR =ENTITIES.register("steelball_mentor",
            () -> EntityType.Builder.<SteelballMentorEntity>of(SteelballMentorEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).eyeHeight(1.7F).clientTrackingRange(10).build("steelball_mentor"));

    public static final DeferredItem<DeferredSpawnEggItem> MENTOR_EGG = ITEMS.register("steelball_mentor_spawn_egg",
            () -> new DeferredSpawnEggItem(MENTOR, 0x3A4A3A, 0xB4C0CC, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        MATERIALS.register(modBus);
        modBus.addListener(SbRegistry::attributes);
        modBus.addListener(SbRegistry::creativeTabs);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(MENTOR.get(), SteelballMentorEntity.createAttributes().build());
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_RITUALS.getKey()) {
            e.accept(STEEL_BALL.get());
            for (DeferredItem<SteelBallItem> b : BALL_TIERS) e.accept(b.get());
            e.accept(RELIC.get());
            for (SbPart p : SbPart.values()) e.accept(PARTS.get(p).get());
            e.accept(SAINT_COMPASS.get());
            e.accept(MENTOR_LETTER.get());
            e.accept(HORSE_WHISTLE.get());
            for (SbOutfitTier tier : SbOutfitTier.values()) {
                for (ArmorItem.Type type : SbOutfitTier.TYPES) e.accept(outfit(tier, type).get());
            }
        } else if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(MENTOR_EGG.get());
        }
    }

    private SbRegistry() {}
}
