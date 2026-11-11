package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.class_knight.entity.SpectralSwordEntity;
import com.renanpontes.isekaimahou.feature.class_knight.item.LegendarySwordItem;
import com.renanpontes.isekaimahou.feature.class_knight.item.RunicBladeItem;
import com.renanpontes.isekaimahou.feature.class_knight.oath.OathAltarBlock;
import com.renanpontes.isekaimahou.feature.class_knight.outfit.KnightArmorItem;
import com.renanpontes.isekaimahou.feature.class_knight.outfit.KnightOutfitTier;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do Cavaleiro Rúnico: lâminas, espadas lendárias, armadura, altar, espada espectral, efeitos e componente. */
public final class KnightRegistries {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, IsekaiMahou.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, IsekaiMahou.MODID);

    // ------------------------------------------------------------------ componente: runas gravadas
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RuneData>> RUNES =
            COMPONENTS.registerComponentType("knight_runes", b -> b.persistent(RuneData.CODEC).networkSynchronized(RuneData.STREAM_CODEC));

    // ------------------------------------------------------------------ lâminas
    /** Graus das Lâminas Rúnicas: ferro → diamante → netherite → além (dano 6,5 / 7,5 / 8,5 / 9,5). */
    private static final Tier[] RUNIC_TIERS = {
            new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 600, 6.5F, 2.5F, 16, () -> Ingredient.of(Items.IRON_INGOT)),
            new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1400, 8.0F, 3.5F, 18, () -> Ingredient.of(Items.DIAMOND)),
            new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2200, 9.0F, 4.5F, 20, () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get())),
            new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 3000, 9.5F, 5.5F, 22, () -> Ingredient.of(Items.NETHERITE_INGOT))};

    /** Arma do kit (id exigido pelo núcleo: {@code MagicPath.KNIGHT.kitWeapon()}). Igual ao grau I. */
    public static final DeferredItem<RunicBladeItem> RUNIC_BLADE = blade("runic_blade", 1);
    public static final List<DeferredItem<RunicBladeItem>> RUNIC_TIERED = List.of(
            blade("runic_blade_t1", 1), blade("runic_blade_t2", 2), blade("runic_blade_t3", 3), blade("runic_blade_t4", 4));

    private static DeferredItem<RunicBladeItem> blade(String id, int t) {
        Tier tier = RUNIC_TIERS[t - 1];
        Rarity rarity = switch (t) {
            case 1 -> Rarity.COMMON;
            case 2 -> Rarity.UNCOMMON;
            case 3 -> Rarity.RARE;
            default -> Rarity.EPIC;
        };
        return ITEMS.register(id, () -> {
            Item.Properties p = new Item.Properties().rarity(rarity).attributes(SwordItem.createAttributes(tier, 3, -2.4F));
            if (t >= 3) p = p.fireResistant();
            return new RunicBladeItem(tier, t, p);
        });
    }

    public static final Map<LegendSword, DeferredItem<LegendarySwordItem>> LEGENDS = new EnumMap<>(LegendSword.class);

    static {
        for (LegendSword s : LegendSword.values()) {
            LEGENDS.put(s, ITEMS.register(s.id, () -> {
                Tier tier = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 4000, 10.0F, s.damageBonus, 25,
                        () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get()));
                return new LegendarySwordItem(s, tier, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
                        .attributes(SwordItem.createAttributes(tier, 3, -2.4F)));
            }));
        }
    }

    // ------------------------------------------------------------------ altar
    public static final DeferredBlock<OathAltarBlock> OATH_ALTAR = BLOCKS.register("knight_oath_altar",
            () -> new OathAltarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.5F, 1200F)
                    .requiresCorrectToolForDrops().sound(SoundType.STONE).noOcclusion().lightLevel(s -> 7)));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> OATH_ALTAR_ITEM = ITEMS.registerSimpleBlockItem("knight_oath_altar", OATH_ALTAR,
            new Item.Properties().rarity(Rarity.UNCOMMON));

    // ------------------------------------------------------------------ espada espectral (visual)
    public static final DeferredHolder<EntityType<?>, EntityType<SpectralSwordEntity>> SPECTRAL_SWORD = ENTITIES.register(
            "knight_spectral_sword", () -> EntityType.Builder.<SpectralSwordEntity>of(SpectralSwordEntity::new, MobCategory.MISC)
                    .sized(0.6F, 2.0F).clientTrackingRange(8).updateInterval(4).noSave().fireImmune().build("knight_spectral_sword"));

    // ------------------------------------------------------------------ efeitos
    public static final DeferredHolder<MobEffect, MobEffect> BLADE_AURA = EFFECTS.register("knight_blade_aura",
            () -> new KnightMobEffect(MobEffectCategory.BENEFICIAL, 0xFFE27A, true)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("knight_blade_aura.damage"), 3.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("knight_blade_aura.speed"), 0.10,
                            AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    public static final DeferredHolder<MobEffect, MobEffect> RUNE_MARK = EFFECTS.register("knight_rune_mark",
            () -> new KnightMobEffect(MobEffectCategory.HARMFUL, 0x8FD8FF, false));
    /** Vento Invisível (Golpe do Ar): a lâmina some envolta em vento — +2 de dano e +1 de alcance. */
    public static final DeferredHolder<MobEffect, MobEffect> INVISIBLE_AIR = EFFECTS.register("knight_invisible_air",
            () -> new InvisibleAirEffect()
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("knight_invisible_air.damage"), 2.0,
                            AttributeModifier.Operation.ADD_VALUE)
                    .addAttributeModifier(Attributes.ENTITY_INTERACTION_RANGE, IsekaiMahou.id("knight_invisible_air.reach"), 1.0,
                            AttributeModifier.Operation.ADD_VALUE));

    // ------------------------------------------------------------------ armadura (4 graus × 4 peças)
    public static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
            ArmorItem.Type.BOOTS};
    private static final Map<KnightOutfitTier, Map<ArmorItem.Type, DeferredItem<KnightArmorItem>>> ARMOR = new EnumMap<>(KnightOutfitTier.class);

    static {
        for (KnightOutfitTier tier : KnightOutfitTier.values()) {
            DeferredHolder<ArmorMaterial, ArmorMaterial> mat = MATERIALS.register(tier.modelName(), () -> material(tier));
            Map<ArmorItem.Type, DeferredItem<KnightArmorItem>> pieces = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type type : TYPES) {
                pieces.put(type, ITEMS.register(tier.itemId(type), () -> {
                    Item.Properties p = new Item.Properties().durability(type.getDurability(tier.durability)).rarity(tier.rarity);
                    if (tier == KnightOutfitTier.RUNEKING) p = p.fireResistant();
                    return new KnightArmorItem(tier, mat, type, p);
                }));
            }
            ARMOR.put(tier, pieces);
        }
    }

    private static ArmorMaterial material(KnightOutfitTier tier) {
        Map<ArmorItem.Type, Integer> def = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type t : ArmorItem.Type.values()) def.put(t, tier.defense(t));
        Holder<net.minecraft.sounds.SoundEvent> sound = switch (tier) {
            case SQUIRE -> SoundEvents.ARMOR_EQUIP_IRON;
            case RUNEGUARD -> SoundEvents.ARMOR_EQUIP_CHAIN;
            case TEMPLAR -> SoundEvents.ARMOR_EQUIP_DIAMOND;
            case RUNEKING -> SoundEvents.ARMOR_EQUIP_NETHERITE;
        };
        return new ArmorMaterial(def, 15, sound,
                tier == KnightOutfitTier.SQUIRE ? () -> Ingredient.of(Items.IRON_INGOT) : () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get()),
                List.of(new ArmorMaterial.Layer(IsekaiMahou.id(tier.modelName()))), tier.toughness, tier.knockback);
    }

    public static DeferredItem<KnightArmorItem> armor(KnightOutfitTier tier, ArmorItem.Type type) {
        return ARMOR.get(tier).get(type);
    }

    /** Todos os itens do módulo, na ordem da aba criativa. */
    public static List<DeferredItem<? extends Item>> creativeOrder() {
        List<DeferredItem<? extends Item>> out = new ArrayList<>();
        out.add(RUNIC_BLADE);
        out.addAll(RUNIC_TIERED);
        for (LegendSword s : LegendSword.values()) out.add(LEGENDS.get(s));
        out.add(OATH_ALTAR_ITEM);
        for (KnightOutfitTier t : KnightOutfitTier.values()) {
            for (ArmorItem.Type type : TYPES) out.add(armor(t, type));
        }
        return out;
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
        COMPONENTS.register(modBus);
        MATERIALS.register(modBus);
    }

    private KnightRegistries() {}
}
