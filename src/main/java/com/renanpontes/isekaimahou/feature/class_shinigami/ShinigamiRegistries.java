package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.BlackCoffinEntity;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.HollowBeamEntity;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.ReishiWaveEntity;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.ZanpakutoSpiritEntity;
import com.renanpontes.isekaimahou.feature.class_shinigami.item.ShinigamiArmorItem;
import com.renanpontes.isekaimahou.feature.class_shinigami.item.SpiritCandyItem;
import com.renanpontes.isekaimahou.feature.class_shinigami.item.ZanpakutoItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Itens, entidades e materiais de armadura da classe Shinigami (ids prefixados com {@code shinigami_}/{@code zanpakuto}). */
public final class ShinigamiRegistries {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, IsekaiMahou.MODID);

    // ------------------------------------------------------------------ entidades
    public static final DeferredHolder<EntityType<?>, EntityType<ZanpakutoSpiritEntity>> SPIRIT = ENTITIES.register("zanpakuto_spirit",
            () -> EntityType.Builder.of(ZanpakutoSpiritEntity::new, MobCategory.MISC).sized(0.6F, 1.95F).clientTrackingRange(10)
                    .fireImmune().build("zanpakuto_spirit"));
    public static final DeferredHolder<EntityType<?>, EntityType<ReishiWaveEntity>> REISHI_WAVE = ENTITIES.register("shinigami_reishi_wave",
            () -> EntityType.Builder.<ReishiWaveEntity>of(ReishiWaveEntity::new, MobCategory.MISC).sized(0.6F, 0.6F)
                    .clientTrackingRange(8).updateInterval(2).build("shinigami_reishi_wave"));
    /** Hadō nº 90: o caixão negro (temporário, nunca salvo). */
    public static final DeferredHolder<EntityType<?>, EntityType<BlackCoffinEntity>> BLACK_COFFIN = ENTITIES.register("shinigami_black_coffin",
            () -> EntityType.Builder.<BlackCoffinEntity>of(BlackCoffinEntity::new, MobCategory.MISC).sized(1.0F, 1.0F).noSave()
                    .clientTrackingRange(8).updateInterval(10).build("shinigami_black_coffin"));
    /** O feixe do Cero (só visual, temporário). */
    public static final DeferredHolder<EntityType<?>, EntityType<HollowBeamEntity>> HOLLOW_BEAM = ENTITIES.register("shinigami_hollow_beam",
            () -> EntityType.Builder.<HollowBeamEntity>of(HollowBeamEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).noSave()
                    .clientTrackingRange(10).updateInterval(20).build("shinigami_hollow_beam"));

    // ------------------------------------------------------------------ zanpakutō (arma do kit + graus)
    /** Graus: ferro → diamante → netherite → além (dano 6,5 / 7,5 / 8,5 / 9,5; um pouco mais rápida que a espada). */
    private static final Tier[] BLADE_TIERS = {
            new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL, 700, 6.5F, 2.5F, 16, () -> Ingredient.of(Items.IRON_INGOT)),
            new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1500, 8.0F, 3.5F, 18, () -> Ingredient.of(Items.DIAMOND)),
            new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 2300, 9.0F, 4.5F, 20, () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get())),
            new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 3100, 9.5F, 5.5F, 22, () -> Ingredient.of(Items.NETHERITE_INGOT))};

    /** Arma do kit: o id é EXATAMENTE o {@code kitWeapon} do MagicPath.SHINIGAMI. Igual ao grau I. */
    public static final DeferredItem<ZanpakutoItem> ZANPAKUTO = blade("zanpakuto", 1);
    /** Graus para a Guilda/Provações ({@code ClassKits.tieredWeaponPrefix} procura {@code zanpakuto_t1}). */
    public static final DeferredItem<ZanpakutoItem> ZANPAKUTO_T1 = blade("zanpakuto_t1", 1);
    public static final DeferredItem<ZanpakutoItem> ZANPAKUTO_T2 = blade("zanpakuto_t2", 2);
    public static final DeferredItem<ZanpakutoItem> ZANPAKUTO_T3 = blade("zanpakuto_t3", 3);
    public static final DeferredItem<ZanpakutoItem> ZANPAKUTO_T4 = blade("zanpakuto_t4", 4);

    private static DeferredItem<ZanpakutoItem> blade(String id, int grade) {
        Tier tier = BLADE_TIERS[grade - 1];
        Rarity rarity = switch (grade) {
            case 1 -> Rarity.COMMON;
            case 2 -> Rarity.UNCOMMON;
            case 3 -> Rarity.RARE;
            default -> Rarity.EPIC;
        };
        return ITEMS.register(id, () -> {
            Item.Properties p = new Item.Properties().rarity(rarity).attributes(SwordItem.createAttributes(tier, 3, -2.2F));
            if (grade >= 3) p = p.fireResistant();
            return new ZanpakutoItem(tier, grade, p);
        });
    }

    public static final DeferredItem<SpiritCandyItem> SPIRIT_CANDY = ITEMS.register("shinigami_spirit_candy",
            () -> new SpiritCandyItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<DeferredSpawnEggItem> SPIRIT_EGG = ITEMS.register("zanpakuto_spirit_spawn_egg",
            () -> new DeferredSpawnEggItem(SPIRIT, 0x1A1622, 0xD9483B, new Item.Properties()));

    // ------------------------------------------------------------------ traje (4 graus)
    static final Map<ShinigamiArmor.Tier, DeferredHolder<ArmorMaterial, ArmorMaterial>> MATERIAL = new EnumMap<>(ShinigamiArmor.Tier.class);

    static {
        for (ShinigamiArmor.Tier tier : ShinigamiArmor.Tier.values()) {
            DeferredHolder<ArmorMaterial, ArmorMaterial> material = MATERIALS.register(tier.modelName(), () -> material(tier));
            MATERIAL.put(tier, material);
            Map<ArmorItem.Type, DeferredItem<ShinigamiArmorItem>> pieces = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type type : ShinigamiArmor.TYPES) {
                pieces.put(type, ITEMS.register(tier.itemId(type), () -> new ShinigamiArmorItem(tier, material, type, props(tier, type))));
            }
            ShinigamiArmor.PIECES.put(tier, pieces);
        }
    }

    private static ArmorMaterial material(ShinigamiArmor.Tier tier) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) defense.put(type, type == ArmorItem.Type.BODY ? 0 : tier.defense(type));
        var sound = switch (tier) {
            case APPRENTICE, ADEPT -> SoundEvents.ARMOR_EQUIP_LEATHER;
            case MASTER -> SoundEvents.ARMOR_EQUIP_CHAIN;
            case ARCHMAGE -> SoundEvents.ARMOR_EQUIP_NETHERITE;
        };
        return new ArmorMaterial(defense, 18, sound,
                tier == ShinigamiArmor.Tier.APPRENTICE
                        ? () -> Ingredient.of(ItemTags.WOOL)
                        : () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get()),
                List.of(new ArmorMaterial.Layer(IsekaiMahou.id(tier.modelName()))), tier.toughness, 0.0F);
    }

    private static Item.Properties props(ShinigamiArmor.Tier tier, ArmorItem.Type type) {
        Item.Properties p = new Item.Properties().durability(type.getDurability(tier.durability)).rarity(tier.rarity);
        return tier == ShinigamiArmor.Tier.ARCHMAGE ? p.fireResistant() : p;
    }

    /** Todas as zanpakutō (para as propriedades do modelo no cliente). */
    public static List<Item> blades() {
        return List.of(ZANPAKUTO.get(), ZANPAKUTO_T1.get(), ZANPAKUTO_T2.get(), ZANPAKUTO_T3.get(), ZANPAKUTO_T4.get());
    }

    /** Itens na ordem da aba criativa. */
    public static List<ItemStack> creativeItems() {
        List<ItemStack> out = new ArrayList<>();
        out.add(new ItemStack(ZANPAKUTO.get()));
        out.add(new ItemStack(ZANPAKUTO_T2.get()));
        out.add(new ItemStack(ZANPAKUTO_T3.get()));
        out.add(new ItemStack(ZANPAKUTO_T4.get()));
        out.add(new ItemStack(SPIRIT_CANDY.get()));
        for (ShinigamiArmor.Tier t : ShinigamiArmor.Tier.values()) {
            for (ArmorItem.Type type : ShinigamiArmor.TYPES) out.add(new ItemStack(ShinigamiArmor.piece(t, type).get()));
        }
        return out;
    }

    public static boolean isBlade(ItemStack s) {
        return s.getItem() instanceof ZanpakutoItem;
    }

    static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        MATERIALS.register(modBus);
    }

    private ShinigamiRegistries() {}
}
