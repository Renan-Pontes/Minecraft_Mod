package com.renanpontes.isekaimahou.feature.creatures_a;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.Goblin;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.GoblinArcher;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.GoblinKing;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.GoblinShaman;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.GoblinWolfRider;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin.Hobgoblin;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.slime.KingSlime;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.slime.MahouSlime;
import com.renanpontes.isekaimahou.feature.creatures_a.entity.slime.SlimeKind;
import com.renanpontes.isekaimahou.feature.creatures_a.item.CrownItem;
import com.renanpontes.isekaimahou.feature.creatures_a.item.CreatureMaterialItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.tags.BlockTags;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do módulo creatures_a: entidades (slimes e goblins), drops, armas, coroas e ovos. */
public final class CaRegistry {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    /** Ovos e drops, na ordem em que aparecem na aba de criaturas. */
    public static final List<DeferredItem<? extends Item>> EGGS = new ArrayList<>();
    public static final List<DeferredItem<? extends Item>> DROPS = new ArrayList<>();

    // ------------------------------------------------------------------ Slimes
    public static final DeferredHolder<EntityType<?>, EntityType<MahouSlime>> SLIME = slime(SlimeKind.BLUE, false);
    public static final DeferredHolder<EntityType<?>, EntityType<MahouSlime>> FIRE_SLIME = slime(SlimeKind.FIRE, true);
    public static final DeferredHolder<EntityType<?>, EntityType<MahouSlime>> WATER_SLIME = slime(SlimeKind.WATER, false);
    public static final DeferredHolder<EntityType<?>, EntityType<MahouSlime>> STONE_SLIME = slime(SlimeKind.STONE, false);
    public static final DeferredHolder<EntityType<?>, EntityType<MahouSlime>> WIND_SLIME = slime(SlimeKind.WIND, false);
    public static final DeferredHolder<EntityType<?>, EntityType<MahouSlime>> POISON_JELLY = slime(SlimeKind.POISON, false);
    public static final DeferredHolder<EntityType<?>, EntityType<KingSlime>> KING_SLIME = ENTITIES.register("king_slime",
            () -> EntityType.Builder.<KingSlime>of(KingSlime::new, MobCategory.MONSTER)
                    .sized(0.52F, 0.52F).eyeHeight(0.325F).spawnDimensionsScale(6.0F).clientTrackingRange(12).build("king_slime"));

    // ------------------------------------------------------------------ Goblins
    public static final DeferredHolder<EntityType<?>, EntityType<Goblin>> GOBLIN = ENTITIES.register("goblin",
            () -> EntityType.Builder.of(Goblin::new, MobCategory.MONSTER).sized(0.6F, 1.25F).eyeHeight(1.05F)
                    .clientTrackingRange(8).build("goblin"));
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinArcher>> GOBLIN_ARCHER = ENTITIES.register("goblin_archer",
            () -> EntityType.Builder.of(GoblinArcher::new, MobCategory.MONSTER).sized(0.6F, 1.25F).eyeHeight(1.05F)
                    .clientTrackingRange(8).build("goblin_archer"));
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinShaman>> GOBLIN_SHAMAN = ENTITIES.register("goblin_shaman",
            () -> EntityType.Builder.of(GoblinShaman::new, MobCategory.MONSTER).sized(0.6F, 1.35F).eyeHeight(1.05F)
                    .clientTrackingRange(8).build("goblin_shaman"));
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinWolfRider>> GOBLIN_WOLF_RIDER = ENTITIES.register("goblin_wolf_rider",
            () -> EntityType.Builder.of(GoblinWolfRider::new, MobCategory.MONSTER).sized(1.0F, 1.75F).eyeHeight(1.55F)
                    .clientTrackingRange(10).build("goblin_wolf_rider"));
    public static final DeferredHolder<EntityType<?>, EntityType<Hobgoblin>> HOBGOBLIN = ENTITIES.register("hobgoblin",
            () -> EntityType.Builder.of(Hobgoblin::new, MobCategory.MONSTER).sized(0.8F, 1.95F).eyeHeight(1.65F)
                    .clientTrackingRange(8).build("hobgoblin"));
    public static final DeferredHolder<EntityType<?>, EntityType<GoblinKing>> GOBLIN_KING = ENTITIES.register("goblin_king",
            () -> EntityType.Builder.of(GoblinKing::new, MobCategory.MONSTER).sized(1.2F, 2.5F).eyeHeight(2.15F)
                    .clientTrackingRange(12).build("goblin_king"));

    // ------------------------------------------------------------------ Drops de slime
    public static final DeferredItem<CreatureMaterialItem> SLIME_GEL = item("slime_gel",
            () -> new CreatureMaterialItem(new Item.Properties(), 0, false));
    public static final DeferredItem<CreatureMaterialItem> FIRE_SLIME_GEL = item("fire_slime_gel",
            () -> new CreatureMaterialItem(new Item.Properties().fireResistant(), 2400, false));
    public static final DeferredItem<CreatureMaterialItem> WATER_SLIME_GEL = item("water_slime_gel",
            () -> new CreatureMaterialItem(new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.2F)
                    .alwaysEdible().fast()
                    .effect(() -> new MobEffectInstance(MobEffects.WATER_BREATHING, 900, 0), 1.0F).build()), 0, true));
    public static final DeferredItem<CreatureMaterialItem> STONE_SLIME_GEL = item("stone_slime_gel",
            () -> new CreatureMaterialItem(new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1F)
                    .alwaysEdible()
                    .effect(() -> new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 0), 1.0F)
                    .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0), 1.0F).build()), 0, false));
    public static final DeferredItem<CreatureMaterialItem> WIND_SLIME_GEL = item("wind_slime_gel",
            () -> new CreatureMaterialItem(new Item.Properties().food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1F)
                    .alwaysEdible().fast()
                    .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0), 1.0F)
                    .effect(() -> new MobEffectInstance(MobEffects.JUMP, 600, 0), 1.0F).build()), 0, false));
    public static final DeferredItem<CreatureMaterialItem> POISON_JELLY_GEL = item("poison_jelly_gel",
            () -> new CreatureMaterialItem(new Item.Properties(), 0, false));
    public static final DeferredItem<CrownItem> KING_SLIME_CROWN = item("king_slime_crown",
            () -> new CrownItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ARMOR, new AttributeModifier(IsekaiMahou.id("king_slime_crown_armor"), 2.0,
                                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
                            .add(Attributes.MAX_HEALTH, new AttributeModifier(IsekaiMahou.id("king_slime_crown_health"), 4.0,
                                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
                            .add(Attributes.SAFE_FALL_DISTANCE, new AttributeModifier(IsekaiMahou.id("king_slime_crown_fall"), 3.0,
                                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
                            .build())));

    // ------------------------------------------------------------------ Drops de goblin
    public static final DeferredItem<Item> GOBLIN_EAR = item("goblin_ear", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> GOBLIN_FETISH = item("goblin_fetish",
            () -> new CreatureMaterialItem(new Item.Properties().rarity(Rarity.UNCOMMON), 0, false));
    public static final DeferredItem<SwordItem> CRUDE_DAGGER = item("crude_dagger",
            () -> new SwordItem(Tiers.STONE, new Item.Properties().attributes(SwordItem.createAttributes(Tiers.STONE, 1, -1.6F))));
    public static final DeferredItem<SwordItem> GOBLIN_CLUB = item("goblin_club",
            () -> new SwordItem(Tiers.WOOD, new Item.Properties().attributes(SwordItem.createAttributes(Tiers.WOOD, 4, -3.0F))));
    /** Ferro reforçado do machado do rei: o TieredItem sobrescreve a durabilidade pela do Tier, então ela mora aqui. */
    private static final Tier GREATAXE_TIER = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 900, 6.0F, 2.0F, 14,
            () -> Ingredient.of(Items.IRON_INGOT));
    public static final DeferredItem<AxeItem> GOBLIN_KING_GREATAXE = item("goblin_king_greataxe",
            () -> new AxeItem(GREATAXE_TIER, new Item.Properties().rarity(Rarity.RARE)
                    .attributes(AxeItem.createAttributes(GREATAXE_TIER, 8.0F, -3.2F))));
    public static final DeferredItem<CrownItem> GOBLIN_KING_CROWN = item("goblin_king_crown",
            () -> new CrownItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ARMOR, new AttributeModifier(IsekaiMahou.id("goblin_king_crown_armor"), 3.0,
                                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
                            .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(IsekaiMahou.id("goblin_king_crown_damage"), 1.0,
                                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
                            .build())));

    // ------------------------------------------------------------------ Ovos (cores: fundo, pintas)
    static {
        egg("slime", SLIME, 0x4FA8FF, 0xD8F0FF);
        egg("fire_slime", FIRE_SLIME, 0xFF7A2E, 0xFFE36B);
        egg("water_slime", WATER_SLIME, 0x3FC6E8, 0x1E5FBF);
        egg("stone_slime", STONE_SLIME, 0x8A8577, 0x5E8C3A);
        egg("wind_slime", WIND_SLIME, 0x9FF0D0, 0xFFFFFF);
        egg("poison_jelly", POISON_JELLY, 0x9B4FD8, 0x8BE04E);
        egg("king_slime", KING_SLIME, 0x3F8FFF, 0xF2C94C);
        egg("goblin", GOBLIN, 0x5E9E3A, 0x7A5232);
        egg("goblin_archer", GOBLIN_ARCHER, 0x5E9E3A, 0x3C5A2A);
        egg("goblin_shaman", GOBLIN_SHAMAN, 0x5E9E3A, 0x6A4A8C);
        egg("goblin_wolf_rider", GOBLIN_WOLF_RIDER, 0x5A5550, 0x5E9E3A);
        egg("hobgoblin", HOBGOBLIN, 0x4F7A33, 0x8A8F96);
        egg("goblin_king", GOBLIN_KING, 0x557F33, 0xE2B23A);
    }

    private static DeferredHolder<EntityType<?>, EntityType<MahouSlime>> slime(SlimeKind kind, boolean fireImmune) {
        return ENTITIES.register(kind.id, () -> {
            EntityType.Builder<MahouSlime> b = EntityType.Builder.<MahouSlime>of((type, level) -> new MahouSlime(type, level, kind),
                    MobCategory.MONSTER).sized(0.52F, 0.52F).eyeHeight(0.325F).spawnDimensionsScale(4.0F).clientTrackingRange(10);
            if (fireImmune) b.fireImmune();
            return b.build(kind.id);
        });
    }

    private static <I extends Item> DeferredItem<I> item(String id, Supplier<I> sup) {
        DeferredItem<I> it = ITEMS.register(id, sup);
        DROPS.add(it);
        return it;
    }

    private static void egg(String id, Supplier<? extends EntityType<? extends net.minecraft.world.entity.Mob>> type, int bg, int fg) {
        EGGS.add(ITEMS.register(id + "_spawn_egg", () -> new DeferredSpawnEggItem(type, bg, fg, new Item.Properties())));
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }

    private CaRegistry() {}
}
