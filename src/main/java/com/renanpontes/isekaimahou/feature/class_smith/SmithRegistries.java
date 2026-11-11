package com.renanpontes.isekaimahou.feature.class_smith;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_smith.entity.BladeWorldEntity;
import com.renanpontes.isekaimahou.feature.class_smith.entity.RhoAiasEntity;
import com.renanpontes.isekaimahou.feature.class_smith.entity.SmithBladeEntity;
import com.renanpontes.isekaimahou.feature.class_smith.entity.SmithSparkEntity;
import com.renanpontes.isekaimahou.feature.class_smith.entity.StarHammerEntity;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgedBlade;
import com.renanpontes.isekaimahou.feature.class_smith.item.ArcaneAnvilBlock;
import com.renanpontes.isekaimahou.feature.class_smith.item.ForgedBladeItem;
import com.renanpontes.isekaimahou.feature.class_smith.item.ProjectionItem;
import com.renanpontes.isekaimahou.feature.class_smith.item.SmithHammerItem;
import com.renanpontes.isekaimahou.feature.class_smith.item.SmithOutfitItem;
import com.renanpontes.isekaimahou.feature.class_smith.item.SmithOutfitTier;

import com.mojang.serialization.Codec;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registros do Ferreiro: itens, bloco, entidades, componente da lâmina e os materiais da roupa. */
public final class SmithRegistries {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, IsekaiMahou.MODID);

    // ---------------------------------------------------------------- componente
    /** As partes da Lâmina Forjada. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ForgedBlade>> BLADE =
            COMPONENTS.registerComponentType("smith_blade", b -> b.persistent(ForgedBlade.CODEC)
                    .networkSynchronized(ForgedBlade.STREAM_CODEC.cast()));

    /** Projeção (Traçar: Ligar): a arma copiada e até quando ela existe (tick do mundo). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemStack>> PROJECTION_SOURCE =
            COMPONENTS.registerComponentType("smith_projection_source", b -> b.persistent(ItemStack.CODEC)
                    .networkSynchronized(ItemStack.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> PROJECTION_UNTIL =
            COMPONENTS.registerComponentType("smith_projection_until", b -> b.persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG));

    // ---------------------------------------------------------------- itens
    /** Arma do kit (id exigido pelo núcleo: {@code MagicPath.SMITH.kitWeapon()}). */
    public static final DeferredItem<SmithHammerItem> SMITH_HAMMER = ITEMS.register("smith_hammer",
            () -> new SmithHammerItem(0, new Item.Properties()));
    /** Graus do martelo ({@code smith_hammer_t1..t4}), entregues pela Guilda/Provações e pelas Provas da Forja. */
    public static final List<DeferredItem<SmithHammerItem>> HAMMER_TIERS = new ArrayList<>();

    static {
        for (int t = 1; t <= 4; t++) {
            final int tier = t;
            HAMMER_TIERS.add(ITEMS.register("smith_hammer_t" + t, () -> new SmithHammerItem(tier,
                    new Item.Properties().rarity(tier >= 4 ? Rarity.EPIC : tier == 3 ? Rarity.RARE : tier == 2 ? Rarity.UNCOMMON : Rarity.COMMON))));
        }
    }

    public static final DeferredItem<ForgedBladeItem> FORGED_BLADE = ITEMS.register("smith_forged_blade",
            () -> new ForgedBladeItem(new Item.Properties()));
    /** Cópia temporária de uma arma (some sozinha). */
    public static final DeferredItem<ProjectionItem> PROJECTION = ITEMS.register("smith_projection",
            () -> new ProjectionItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<Item> STAR_METAL = ITEMS.register("smith_star_metal",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE).fireResistant()));

    // ---------------------------------------------------------------- bloco
    public static final DeferredBlock<ArcaneAnvilBlock> ARCANE_ANVIL = BLOCKS.register("smith_arcane_anvil",
            () -> new ArcaneAnvilBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.0F, 1200.0F)
                    .sound(SoundType.ANVIL).requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> 6)
                    .pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> ARCANE_ANVIL_ITEM = ITEMS.register("smith_arcane_anvil",
            () -> new BlockItem(ARCANE_ANVIL.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));

    // ---------------------------------------------------------------- roupa (4 graus x 4 peças)
    private static final Map<SmithOutfitTier, DeferredHolder<ArmorMaterial, ArmorMaterial>> MATERIAL = new EnumMap<>(SmithOutfitTier.class);
    private static final Map<SmithOutfitTier, Map<ArmorItem.Type, DeferredItem<SmithOutfitItem>>> PIECES = new EnumMap<>(SmithOutfitTier.class);

    static {
        for (SmithOutfitTier tier : SmithOutfitTier.VALUES) {
            DeferredHolder<ArmorMaterial, ArmorMaterial> material = MATERIALS.register(tier.materialId(), () -> material(tier));
            MATERIAL.put(tier, material);
            Map<ArmorItem.Type, DeferredItem<SmithOutfitItem>> pieces = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type type : SmithOutfitTier.TYPES) {
                Item.Properties props = new Item.Properties().durability(type.getDurability(tier.durabilityFactor)).rarity(tier.rarity);
                if (tier == SmithOutfitTier.ARCHMAGE) props = props.fireResistant();
                final Item.Properties fp = props;
                pieces.put(type, ITEMS.register(tier.itemId(type), () -> new SmithOutfitItem(tier, material, type, fp)));
            }
            PIECES.put(tier, pieces);
        }
    }

    private static ArmorMaterial material(SmithOutfitTier tier) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            defense.put(type, type == ArmorItem.Type.BODY ? tier.defense(ArmorItem.Type.CHESTPLATE) : tier.defense(type));
        }
        Holder<net.minecraft.sounds.SoundEvent> sound = switch (tier) {
            case APPRENTICE -> SoundEvents.ARMOR_EQUIP_LEATHER;
            case ADEPT -> SoundEvents.ARMOR_EQUIP_CHAIN;
            case MASTER -> SoundEvents.ARMOR_EQUIP_IRON;
            case ARCHMAGE -> SoundEvents.ARMOR_EQUIP_NETHERITE;
        };
        return new ArmorMaterial(defense, 15, sound,
                () -> tier == SmithOutfitTier.APPRENTICE ? Ingredient.of(Items.LEATHER, Items.IRON_INGOT)
                        : tier == SmithOutfitTier.ARCHMAGE ? Ingredient.of(STAR_METAL.get()) : Ingredient.of(Items.IRON_INGOT),
                List.of(new ArmorMaterial.Layer(IsekaiMahou.id(tier.materialId()))), tier.toughness, tier.knockbackResistance);
    }

    public static DeferredItem<SmithOutfitItem> piece(SmithOutfitTier tier, ArmorItem.Type type) {
        return PIECES.get(tier).get(type);
    }

    // ---------------------------------------------------------------- entidades
    public static final DeferredHolder<EntityType<?>, EntityType<SmithSparkEntity>> SPARK = ENTITIES.register("smith_spark",
            () -> EntityType.Builder.<SmithSparkEntity>of(SmithSparkEntity::new, MobCategory.MISC).sized(0.25F, 0.25F)
                    .clientTrackingRange(6).updateInterval(1).noSave().build("smith_spark"));
    public static final DeferredHolder<EntityType<?>, EntityType<StarHammerEntity>> STAR_HAMMER = ENTITIES.register("smith_star_hammer",
            () -> EntityType.Builder.<StarHammerEntity>of(StarHammerEntity::new, MobCategory.MISC).sized(2.5F, 4.0F)
                    .clientTrackingRange(10).updateInterval(2).noSave().fireImmune().build("smith_star_hammer"));

    /** Arma projetada (Traçar, Kanshō e Bakuya, Caladbolg II, Espada Mágica Efêmera, Mundo das Lâminas). */
    public static final DeferredHolder<EntityType<?>, EntityType<SmithBladeEntity>> FLYING_BLADE = ENTITIES.register("smith_blade",
            () -> EntityType.Builder.<SmithBladeEntity>of(SmithBladeEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(8).updateInterval(1).noSave().fireImmune().build("smith_blade"));
    /** Rho Aias: o escudo de sete pétalas. */
    public static final DeferredHolder<EntityType<?>, EntityType<RhoAiasEntity>> RHO_AIAS = ENTITIES.register("smith_rho_aias",
            () -> EntityType.Builder.<RhoAiasEntity>of(RhoAiasEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(8).updateInterval(1).noSave().fireImmune().build("smith_rho_aias"));
    /** Mundo das Lâminas (controlador invisível da forma G). */
    public static final DeferredHolder<EntityType<?>, EntityType<BladeWorldEntity>> BLADE_WORLD = ENTITIES.register("smith_blade_world",
            () -> EntityType.Builder.<BladeWorldEntity>of(BladeWorldEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(10).updateInterval(20).noSave().fireImmune().build("smith_blade_world"));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        MATERIALS.register(modBus);
    }

    private SmithRegistries() {}
}
