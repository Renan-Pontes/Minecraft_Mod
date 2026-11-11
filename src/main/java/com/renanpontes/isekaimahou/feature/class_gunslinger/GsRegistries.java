package com.renanpontes.isekaimahou.feature.class_gunslinger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.class_gunslinger.entity.ManaBulletEntity;
import com.renanpontes.isekaimahou.feature.class_gunslinger.entity.OutlawEntity;
import com.renanpontes.isekaimahou.feature.class_gunslinger.entity.PhotonSlashEntity;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.GsOutfitTier;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.GunslingerArmorItem;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.ManaGunItem;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registros do módulo class_gunslinger: as armas de mana (revólver do kit + tiers I–IV para Guilda/Provações e as
 * outras quatro armas), o traje de 4 graus, a bala de mana, o Forasteiro Espectral (duelo ao meio-dia) e o componente
 * com os cartuchos no tambor.
 */
public final class GsRegistries {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, IsekaiMahou.MODID);

    /** Cartuchos no tambor da arma (ausente = cheio, para a arma nova já sair carregada). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GUN_ROUNDS =
            COMPONENTS.registerComponentType("gun_rounds", b -> b.persistent(Codec.intRange(0, 64)).networkSynchronized(ByteBufCodecs.VAR_INT));

    // ---------------------------------------------------------------- armas
    /** Revólver de Mana do kit (id exigido pelo MagicPath: {@code mana_revolver}). */
    public static final DeferredItem<ManaGunItem> MANA_REVOLVER = ITEMS.register("mana_revolver",
            () -> new ManaGunItem(GunKind.REVOLVER, 0, gunProps(0)));
    /** Revólveres de tier I–IV ({@code mana_revolver_t1..t4}) para quem entrega armas de tier. */
    public static final List<DeferredItem<ManaGunItem>> REVOLVER_TIERS = new ArrayList<>();
    /** As outras armas (uma por tipo; a força vem do rank do Caminho do Gatilho). */
    public static final Map<GunKind, DeferredItem<ManaGunItem>> GUNS = new EnumMap<>(GunKind.class);

    static {
        for (int t = 1; t <= 4; t++) {
            int tier = t;
            REVOLVER_TIERS.add(ITEMS.register("mana_revolver_t" + t, () -> new ManaGunItem(GunKind.REVOLVER, tier, gunProps(tier))));
        }
        GUNS.put(GunKind.REVOLVER, MANA_REVOLVER);
        for (GunKind k : GunKind.values()) {
            if (k == GunKind.REVOLVER) continue;
            GUNS.put(k, ITEMS.register(k.itemId(), () -> new ManaGunItem(k, 0, gunProps(2))));
        }
    }

    private static Item.Properties gunProps(int tier) {
        Item.Properties p = new Item.Properties().stacksTo(1).rarity(switch (tier) {
            case 0, 1 -> net.minecraft.world.item.Rarity.COMMON;
            case 2 -> net.minecraft.world.item.Rarity.UNCOMMON;
            case 3 -> net.minecraft.world.item.Rarity.RARE;
            default -> net.minecraft.world.item.Rarity.EPIC;
        });
        return tier >= 4 ? p.fireResistant() : p;
    }

    // ---------------------------------------------------------------- traje
    private static final Map<GsOutfitTier, DeferredHolder<ArmorMaterial, ArmorMaterial>> MATERIAL = new EnumMap<>(GsOutfitTier.class);
    private static final Map<GsOutfitTier, Map<ArmorItem.Type, DeferredItem<GunslingerArmorItem>>> PIECES = new EnumMap<>(GsOutfitTier.class);

    static {
        for (GsOutfitTier tier : GsOutfitTier.values()) {
            DeferredHolder<ArmorMaterial, ArmorMaterial> mat = MATERIALS.register(tier.materialId(), () -> buildMaterial(tier));
            MATERIAL.put(tier, mat);
            Map<ArmorItem.Type, DeferredItem<GunslingerArmorItem>> m = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type type : GsOutfitTier.TYPES) {
                m.put(type, ITEMS.register(tier.itemId(type), () -> new GunslingerArmorItem(tier, mat, type, armorProps(tier, type))));
            }
            PIECES.put(tier, m);
        }
    }

    private static ArmorMaterial buildMaterial(GsOutfitTier tier) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) defense.put(type, tier.defense(type));
        var sound = switch (tier) {
            case APPRENTICE, ADEPT -> SoundEvents.ARMOR_EQUIP_LEATHER;
            case MASTER -> SoundEvents.ARMOR_EQUIP_GOLD;
            case ARCHMAGE -> SoundEvents.ARMOR_EQUIP_NETHERITE;
        };
        return new ArmorMaterial(defense, 20, sound,
                tier == GsOutfitTier.APPRENTICE ? () -> Ingredient.of(Items.LEATHER) : () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get()),
                // textura vanilla de reserva (o GeckoLib desenha o modelo 3D por cima)
                List.of(new ArmorMaterial.Layer(IsekaiMahou.id(tier.materialId()))), tier.toughness, 0.0F);
    }

    private static Item.Properties armorProps(GsOutfitTier tier, ArmorItem.Type type) {
        Item.Properties p = new Item.Properties().durability(type.getDurability(tier.durabilityFactor)).rarity(tier.rarity);
        return tier == GsOutfitTier.ARCHMAGE ? p.fireResistant() : p;
    }

    public static DeferredItem<GunslingerArmorItem> piece(GsOutfitTier tier, ArmorItem.Type type) {
        return PIECES.get(tier).get(type);
    }

    // ---------------------------------------------------------------- entidades
    public static final DeferredHolder<EntityType<?>, EntityType<ManaBulletEntity>> MANA_BULLET = ENTITIES.register("mana_bullet",
            () -> EntityType.Builder.<ManaBulletEntity>of(ManaBulletEntity::new, MobCategory.MISC).sized(0.25F, 0.25F)
                    .clientTrackingRange(8).updateInterval(1).build("mana_bullet"));
    public static final DeferredHolder<EntityType<?>, EntityType<OutlawEntity>> OUTLAW = ENTITIES.register("gunslinger_outlaw",
            () -> EntityType.Builder.of(OutlawEntity::new, MobCategory.MONSTER).sized(0.6F, 1.95F).eyeHeight(1.62F)
                    .clientTrackingRange(10).build("gunslinger_outlaw"));
    /** Corte da Espada de Fótons / Rajada Estelar (só visual). */
    public static final DeferredHolder<EntityType<?>, EntityType<PhotonSlashEntity>> PHOTON_SLASH = ENTITIES.register("photon_slash",
            () -> EntityType.Builder.<PhotonSlashEntity>of(PhotonSlashEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(8).updateInterval(20).noSave().build("photon_slash"));
    public static final DeferredItem<DeferredSpawnEggItem> OUTLAW_EGG = ITEMS.register("gunslinger_outlaw_spawn_egg",
            () -> new DeferredSpawnEggItem(OUTLAW, 0x3A2A1E, 0xD9A441, new Item.Properties()));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        MATERIALS.register(modBus);
        ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        modBus.addListener(GsRegistries::onAttributes);
        modBus.addListener(GsRegistries::onCreativeTabs);
    }

    private static void onAttributes(EntityAttributeCreationEvent e) {
        e.put(OUTLAW.get(), OutlawEntity.createAttributes().build());
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) {
            e.accept(MANA_REVOLVER.get());
            for (DeferredItem<ManaGunItem> t : REVOLVER_TIERS) e.accept(t.get());
            for (GunKind k : GunKind.values()) if (k != GunKind.REVOLVER) e.accept(GUNS.get(k).get());
        } else if (e.getTabKey() == CoreRegistries.TAB_RITUALS.getKey()) {
            for (GsOutfitTier tier : GsOutfitTier.values()) {
                for (ArmorItem.Type type : GsOutfitTier.TYPES) e.accept(piece(tier, type).get());
            }
        } else if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            e.accept(OUTLAW_EGG.get());
        }
    }

    private GsRegistries() {}
}
