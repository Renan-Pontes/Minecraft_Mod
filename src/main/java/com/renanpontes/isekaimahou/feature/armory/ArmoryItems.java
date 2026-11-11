package com.renanpontes.isekaimahou.feature.armory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.armory.item.ArcaneUpgradeTemplateItem;
import com.renanpontes.isekaimahou.feature.armory.item.ArcaneWeaponItem;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryBowItem;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryCrossbowItem;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryCurioItem;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryLuteItem;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryMeleeItem;
import com.renanpontes.isekaimahou.feature.armory.item.KnightShieldItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.component.DataComponents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Itens do Arsenal: 60 armas (15 tipos × 4 tiers — 5 focos das Mágicas e 10 armas avulsas da v0.9), o Escudo do
 * Cavaleiro (avulso), 3 acessórios de marco, molde de melhoria e Diamante Arcano.
 */
public final class ArmoryItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, IsekaiMahou.MODID);

    /** Armas por tipo; índice 0 = tier I. */
    public static final Map<WeaponKind, List<DeferredItem<Item>>> WEAPONS = new EnumMap<>(WeaponKind.class);

    static {
        for (WeaponKind kind : WeaponKind.values()) {
            List<DeferredItem<Item>> tiers = new ArrayList<>();
            for (int t = 1; t <= WeaponKind.MAX_TIER; t++) {
                final int tier = t;
                tiers.add(ITEMS.register(kind.itemId(tier), () -> createWeapon(kind, tier)));
            }
            WEAPONS.put(kind, List.copyOf(tiers));
        }
    }

    /** Escudo do Cavaleiro (um só, sem tiers): vai junto com a Lâmina do Cavaleiro. */
    public static final DeferredItem<KnightShieldItem> KNIGHT_SHIELD = ITEMS.register("knight_shield",
            () -> new KnightShieldItem(new Item.Properties().rarity(Rarity.UNCOMMON)));

    /** Talismã da Vitalidade (charm): +8 de vida máxima (4 corações; v1.0: a vida base dobrou, era +4). */
    public static final DeferredItem<ArmoryCurioItem> VITALITY_CHARM = ITEMS.register("vitality_charm",
            () -> new ArmoryCurioItem(new Item.Properties().rarity(Rarity.UNCOMMON),
                    SoundEvents.ARMOR_EQUIP_GENERIC.value(), ParticleTypes.HEART,
                    new ArmoryCurioItem.Bonus(Attributes.MAX_HEALTH, 8.0, AttributeModifier.Operation.ADD_VALUE)));
    /** Anel da Guarda (ring): +3 de armadura e +1,5 de resistência de armadura (v1.0: ×1,5 da v0.8). */
    public static final DeferredItem<ArmoryCurioItem> WARD_RING = ITEMS.register("ward_ring",
            () -> new ArmoryCurioItem(new Item.Properties().rarity(Rarity.RARE),
                    SoundEvents.ARMOR_EQUIP_IRON.value(), ParticleTypes.ENCHANTED_HIT,
                    new ArmoryCurioItem.Bonus(Attributes.ARMOR, 3.0, AttributeModifier.Operation.ADD_VALUE),
                    new ArmoryCurioItem.Bonus(Attributes.ARMOR_TOUGHNESS, 1.5, AttributeModifier.Operation.ADD_VALUE)));
    /** Sigilo do Arquimago (necklace): +15% de Poder Mágico. */
    public static final DeferredItem<ArmoryCurioItem> ARCHMAGE_SIGIL = ITEMS.register("archmage_sigil",
            () -> new ArmoryCurioItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant(),
                    SoundEvents.AMETHYST_BLOCK_RESONATE, ParticleTypes.ENCHANT,
                    new ArmoryCurioItem.Bonus(CoreRegistries.SPELL_POWER, 0.15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE)));

    /** Molde de Melhoria Arcana (Mesa de Ferraria: tier N → N+1). */
    public static final DeferredItem<ArcaneUpgradeTemplateItem> ARCANE_UPGRADE_TEMPLATE = ITEMS.register("arcane_upgrade_template",
            ArcaneUpgradeTemplateItem::new);
    /** Diamante Arcano: diamante infundido com essências, material da melhoria para o tier III. */
    public static final DeferredItem<Item> ARCANE_DIAMOND = ITEMS.register("arcane_diamond",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE)) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
                }
            });

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("armory", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.isekai_mahou.armory"))
            .withTabsBefore(CoreRegistries.TAB_WORLD.getKey())
            .icon(() -> new ItemStack(weapon(WeaponKind.MAGE_STAFF, 4)))
            .displayItems((params, out) -> {
                for (WeaponKind kind : WeaponKind.values()) {
                    for (DeferredItem<Item> w : WEAPONS.get(kind)) out.accept(w.get());
                    if (kind == WeaponKind.KNIGHT_BLADE) out.accept(KNIGHT_SHIELD.get());
                }
                out.accept(VITALITY_CHARM.get());
                out.accept(WARD_RING.get());
                out.accept(ARCHMAGE_SIGIL.get());
                out.accept(ARCANE_UPGRADE_TEMPLATE.get());
                out.accept(ARCANE_DIAMOND.get());
            }).build());

    private static Item createWeapon(WeaponKind kind, int tier) {
        Item.Properties props = new Item.Properties().rarity(WeaponKind.rarity(tier));
        if (tier >= WeaponKind.MAX_TIER) {
            // Tier IV: não queima na lava e brilha como item encantado
            props.fireResistant().component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return switch (kind.style) {
            case STAFF, WAND -> new ArcaneWeaponItem(kind, tier, props);
            case LUTE -> new ArmoryLuteItem(kind, tier, props);
            case BOW -> new ArmoryBowItem(kind, tier, props);
            case CROSSBOW -> new ArmoryCrossbowItem(kind, tier, props);
            default -> new ArmoryMeleeItem(kind, tier, props);
        };
    }

    /** A arma do tipo e tier (1..4). */
    public static Item weapon(WeaponKind kind, int tier) {
        return WEAPONS.get(kind).get(Math.max(1, Math.min(WeaponKind.MAX_TIER, tier)) - 1).get();
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
        modBus.addListener(ArmoryItems::fillOtherTabs);
    }

    /** Também nas abas de Magia (acessórios) e de Combate do vanilla (armas de golpe, arcos, bestas e o escudo). */
    private static void fillOtherTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) {
            event.accept(VITALITY_CHARM.get());
            event.accept(WARD_RING.get());
            event.accept(ARCHMAGE_SIGIL.get());
            event.accept(ARCANE_UPGRADE_TEMPLATE.get());
        } else if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            for (WeaponKind kind : WeaponKind.values()) {
                if (!kind.isMelee() && !kind.isRanged()) continue;
                for (DeferredItem<Item> w : WEAPONS.get(kind)) event.accept(w.get());
            }
            event.accept(KNIGHT_SHIELD.get());
        }
    }

    private ArmoryItems() {}
}
