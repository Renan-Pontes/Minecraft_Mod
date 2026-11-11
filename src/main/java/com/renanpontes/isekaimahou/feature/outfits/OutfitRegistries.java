package com.renanpontes.isekaimahou.feature.outfits;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicPath;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Materiais de armadura (um por traje e grau) e as 240 peças (15 trajes × 4 graus × 4 peças):
 * {@code <classe>_<peça>} (grau I, ids antigos) e {@code <classe>_<adept|master|archmage>_<peça>}.
 */
public final class OutfitRegistries {
    public static final DeferredRegister<ArmorMaterial> MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, IsekaiMahou.MODID);

    /** Receita de reforma (troca a classe da peça mantendo grau, desgaste e encantamentos). */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<OutfitRetailorRecipe>> RETAILOR =
            SERIALIZERS.register("outfit_retailor", () -> new RecipeSerializer<>() {
                @Override
                public MapCodec<OutfitRetailorRecipe> codec() {
                    return OutfitRetailorRecipe.CODEC;
                }

                @Override
                public StreamCodec<RegistryFriendlyByteBuf, OutfitRetailorRecipe> streamCodec() {
                    return OutfitRetailorRecipe.STREAM_CODEC;
                }
            });

    /** Ordem das peças (cabeça -> pés), usada no kit, na aba criativa, na entrega e no comando. */
    public static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
            ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

    private static final Map<OutfitSet, Map<OutfitTier, DeferredHolder<ArmorMaterial, ArmorMaterial>>> MATERIAL =
            new EnumMap<>(OutfitSet.class);
    private static final Map<OutfitSet, Map<OutfitTier, Map<ArmorItem.Type, DeferredItem<OutfitArmorItem>>>> PIECES =
            new EnumMap<>(OutfitSet.class);

    static {
        for (OutfitTier tier : OutfitTier.values()) {
            for (OutfitSet set : OutfitSet.values()) {
                DeferredHolder<ArmorMaterial, ArmorMaterial> material = MATERIALS.register(tier.materialId(set),
                        () -> buildMaterial(set, tier));
                MATERIAL.computeIfAbsent(set, s -> new EnumMap<>(OutfitTier.class)).put(tier, material);
                Map<ArmorItem.Type, DeferredItem<OutfitArmorItem>> pieces = new EnumMap<>(ArmorItem.Type.class);
                for (ArmorItem.Type type : TYPES) {
                    pieces.put(type, ITEMS.register(tier.itemId(set, type),
                            () -> new OutfitArmorItem(set, tier, material, type, properties(set, tier, type))));
                }
                PIECES.computeIfAbsent(set, s -> new EnumMap<>(OutfitTier.class)).put(tier, pieces);
            }
        }
    }

    private static ArmorMaterial buildMaterial(OutfitSet set, OutfitTier tier) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) defense.put(type, OutfitStats.defense(set, tier, type));
        // tecido "mágico" aceita bem encantamentos (25, como ouro); metal das Guerreiras 15; couro das À Distância 20
        return new ArmorMaterial(defense, OutfitStats.enchantability(set), equipSound(set, tier), repairIngredient(set, tier),
                // textura vanilla de reserva (o GeckoLib desenha o modelo 3D por cima disso)
                List.of(new ArmorMaterial.Layer(IsekaiMahou.id(tier.materialId(set)))),
                OutfitStats.toughness(set, tier), OutfitStats.knockback(set, tier));
    }

    /**
     * Grau I: lã ou couro nas Mágicas e À Distância (como antes); ferro ou couro nas Guerreiras (cota, placas, fivelas).
     * Graus II–IV: tecido/metal encantado se remenda com Cristal de Mana.
     */
    private static Supplier<Ingredient> repairIngredient(OutfitSet set, OutfitTier tier) {
        if (tier != OutfitTier.APPRENTICE) return () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get());
        if (set.category() == MagicPath.Category.PHYSICAL) {
            return () -> Ingredient.of(Items.IRON_INGOT, Items.LEATHER);
        }
        return () -> Ingredient.fromValues(Stream.of(new Ingredient.TagValue(ItemTags.WOOL),
                new Ingredient.ItemValue(new ItemStack(Items.LEATHER))));
    }

    /** Graus altos têm ornamentos de metal: o som de vestir pesa um pouco mais a cada grau. */
    private static Holder<SoundEvent> equipSound(OutfitSet set, OutfitTier tier) {
        return switch (tier) {
            case APPRENTICE, ADEPT -> set.equipSound;
            case MASTER -> set.category() == MagicPath.Category.PHYSICAL ? SoundEvents.ARMOR_EQUIP_DIAMOND
                    : SoundEvents.ARMOR_EQUIP_GOLD;
            case ARCHMAGE -> SoundEvents.ARMOR_EQUIP_NETHERITE;
        };
    }

    private static Item.Properties properties(OutfitSet set, OutfitTier tier, ArmorItem.Type type) {
        Item.Properties props = new Item.Properties()
                .durability(type.getDurability(OutfitStats.durabilityFactor(set, tier))).rarity(tier.rarity);
        // o traje de Arquimago sobrevive à lava como o netherite
        return tier == OutfitTier.ARCHMAGE ? props.fireResistant() : props;
    }

    /** Peça do grau I (kit inicial). */
    public static DeferredItem<OutfitArmorItem> piece(OutfitSet set, ArmorItem.Type type) {
        return piece(set, OutfitTier.APPRENTICE, type);
    }

    public static DeferredItem<OutfitArmorItem> piece(OutfitSet set, OutfitTier tier, ArmorItem.Type type) {
        return PIECES.get(set).get(tier).get(type);
    }

    public static DeferredHolder<ArmorMaterial, ArmorMaterial> material(OutfitSet set, OutfitTier tier) {
        return MATERIAL.get(set).get(tier);
    }

    public static void register(IEventBus modBus) {
        MATERIALS.register(modBus);
        ITEMS.register(modBus);
        SERIALIZERS.register(modBus);
    }

    private OutfitRegistries() {}
}
