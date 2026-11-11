package com.renanpontes.isekaimahou.feature.class_rogue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.class_rogue.entity.RogueAfterimageEntity;
import com.renanpontes.isekaimahou.feature.class_rogue.entity.RogueKnifeEntity;
import com.renanpontes.isekaimahou.feature.class_rogue.item.RogueArmorItem;
import com.renanpontes.isekaimahou.feature.class_rogue.item.RogueDaggerItem;
import com.renanpontes.isekaimahou.feature.class_rogue.item.StolenGrimoireItem;
import com.renanpontes.isekaimahou.feature.class_rogue.item.ThiefLedgerItem;
import com.renanpontes.isekaimahou.feature.class_rogue.item.ThrowingKnifeItem;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Registros do Ladino: a arma do kit ({@code rogue_dagger}, o id que o núcleo espera) e as adagas de tier
 * {@code rogue_dagger_t1..t4} (Guilda/Provações), facas de arremesso, o Grimório Roubado, o Livro-Razão do Ladrão,
 * o Traje das Sombras (4 graus × 4 peças, GeckoLib), a faca voadora (entidade temporária) e os dados do Ladino
 * (anexo salvo no jogador, copiado na morte).
 */
public final class RogueContent {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, IsekaiMahou.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, IsekaiMahou.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, IsekaiMahou.MODID);

    /** Dados do Ladino: páginas do Grimório Roubado e a página escolhida (ver {@link RogueData}). */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CompoundTag>> DATA = ATTACHMENTS.register("rogue_data",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).copyOnDeath().build());

    // ---------------------------------------------------------------- Adagas
    /** Arma do kit (MagicPath.ROGUE.kitWeapon): Adaga Furtiva. */
    public static final DeferredItem<RogueDaggerItem> DAGGER = ITEMS.register("rogue_dagger",
            () -> new RogueDaggerItem(0, Tiers.IRON, 1, -1.6F, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<RogueDaggerItem> DAGGER_T1 = ITEMS.register("rogue_dagger_t1",
            () -> new RogueDaggerItem(1, Tiers.IRON, 2, -1.6F, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<RogueDaggerItem> DAGGER_T2 = ITEMS.register("rogue_dagger_t2",
            () -> new RogueDaggerItem(2, Tiers.DIAMOND, 2, -1.5F, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<RogueDaggerItem> DAGGER_T3 = ITEMS.register("rogue_dagger_t3",
            () -> new RogueDaggerItem(3, Tiers.DIAMOND, 3, -1.4F, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<RogueDaggerItem> DAGGER_T4 = ITEMS.register("rogue_dagger_t4",
            () -> new RogueDaggerItem(4, Tiers.NETHERITE, 3, -1.3F, new Item.Properties().rarity(Rarity.EPIC).fireResistant()));

    // ---------------------------------------------------------------- Ferramentas do ofício
    public static final DeferredItem<ThrowingKnifeItem> THROWING_KNIFE = ITEMS.register("rogue_throwing_knife",
            () -> new ThrowingKnifeItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<StolenGrimoireItem> STOLEN_GRIMOIRE = ITEMS.register("rogue_stolen_grimoire",
            () -> new StolenGrimoireItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<ThiefLedgerItem> LEDGER = ITEMS.register("rogue_ledger",
            () -> new ThiefLedgerItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    // ---------------------------------------------------------------- Traje das Sombras
    private static final Map<RogueGear, DeferredHolder<ArmorMaterial, ArmorMaterial>> MATERIAL = new EnumMap<>(RogueGear.class);
    private static final Map<RogueGear, Map<ArmorItem.Type, DeferredItem<RogueArmorItem>>> PIECES = new EnumMap<>(RogueGear.class);

    static {
        for (RogueGear gear : RogueGear.values()) {
            DeferredHolder<ArmorMaterial, ArmorMaterial> material = MATERIALS.register(gear.materialId(), () -> buildMaterial(gear));
            MATERIAL.put(gear, material);
            Map<ArmorItem.Type, DeferredItem<RogueArmorItem>> pieces = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type type : RogueGear.TYPES) {
                pieces.put(type, ITEMS.register(gear.itemId(type), () -> new RogueArmorItem(gear, material, type, armorProps(gear, type))));
            }
            PIECES.put(gear, pieces);
        }
    }

    // ---------------------------------------------------------------- Entidade
    /** Faca voadora: do Leque de Facas (magia) e das facas de arremesso. Temporária (não é salva). */
    public static final DeferredHolder<EntityType<?>, EntityType<RogueKnifeEntity>> KNIFE = ENTITIES.register("rogue_knife",
            () -> EntityType.Builder.<RogueKnifeEntity>of(RogueKnifeEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).noSave().clientTrackingRange(6).updateInterval(2).build("rogue_knife"));
    /** Imagem residual (Ritmo de Eco, Velocidade Divina): só visual, some sozinha. */
    public static final DeferredHolder<EntityType<?>, EntityType<RogueAfterimageEntity>> AFTERIMAGE = ENTITIES.register("rogue_afterimage",
            () -> EntityType.Builder.<RogueAfterimageEntity>of(RogueAfterimageEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).noSave().clientTrackingRange(8).updateInterval(1).build("rogue_afterimage"));

    private static ArmorMaterial buildMaterial(RogueGear gear) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) defense.put(type, gear.defense(type));
        Holder<SoundEvent> sound = switch (gear) {
            case T1, T2 -> SoundEvents.ARMOR_EQUIP_LEATHER;
            case T3 -> SoundEvents.ARMOR_EQUIP_CHAIN;
            case T4 -> SoundEvents.ARMOR_EQUIP_NETHERITE;
        };
        return new ArmorMaterial(defense, 20, sound,
                gear == RogueGear.T1 ? () -> Ingredient.of(Items.LEATHER, Items.BLACK_WOOL) : () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get()),
                // textura vanilla de reserva (o GeckoLib desenha o modelo 3D por cima disso)
                List.of(new ArmorMaterial.Layer(IsekaiMahou.id(gear.materialId()))), gear.toughness, gear.knockback);
    }

    private static Item.Properties armorProps(RogueGear gear, ArmorItem.Type type) {
        Item.Properties p = new Item.Properties().durability(type.getDurability(gear.durability)).rarity(gear.rarity);
        return gear == RogueGear.T4 ? p.fireResistant() : p;
    }

    public static DeferredItem<RogueArmorItem> piece(RogueGear gear, ArmorItem.Type type) {
        return PIECES.get(gear).get(type);
    }

    public static List<DeferredItem<RogueDaggerItem>> daggers() {
        return List.of(DAGGER, DAGGER_T1, DAGGER_T2, DAGGER_T3, DAGGER_T4);
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        MATERIALS.register(modBus);
        ENTITIES.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(RogueContent::tabs);
    }

    private static void tabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) {
            for (DeferredItem<RogueDaggerItem> d : daggers()) e.accept(d.get());
            e.accept(THROWING_KNIFE.get());
            e.accept(STOLEN_GRIMOIRE.get());
            e.accept(LEDGER.get());
        }
        if (e.getTabKey() == CoreRegistries.TAB_RITUALS.getKey()) {
            for (RogueGear gear : RogueGear.values()) {
                for (ArmorItem.Type type : RogueGear.TYPES) e.accept(piece(gear, type).get());
            }
        }
    }

    private RogueContent() {}
}
