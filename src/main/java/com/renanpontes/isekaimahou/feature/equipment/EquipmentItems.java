package com.renanpontes.isekaimahou.feature.equipment;

import java.util.EnumMap;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.equipment.item.AttributeCurioItem;
import com.renanpontes.isekaimahou.feature.equipment.item.MagicFocusItem;
import com.renanpontes.isekaimahou.feature.equipment.item.MeditationCharmItem;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Itens do módulo: focos (espaço "magic_focus"), anéis, amuleto e talismã (espaços padrão do Curios). */
public final class EquipmentItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);

    /** Foco comum: +15% nas magias do elemento (e nas fusões dele). */
    public static final float FOCUS_BONUS = 0.15F;
    /** Foco Maior: +30%. */
    public static final float GREATER_FOCUS_BONUS = 0.30F;
    /** Foco Arcano: +8% em qualquer elemento. */
    public static final float ARCANE_FOCUS_BONUS = 0.08F;

    public static final Map<Element, DeferredItem<MagicFocusItem>> FOCI = new EnumMap<>(Element.class);
    public static final Map<Element, DeferredItem<MagicFocusItem>> GREATER_FOCI = new EnumMap<>(Element.class);

    static {
        for (Element e : Element.BASE) {
            FOCI.put(e, ITEMS.register("focus_" + e.id,
                    () -> new MagicFocusItem(e, FOCUS_BONUS, false, curio(Rarity.UNCOMMON))));
            GREATER_FOCI.put(e, ITEMS.register("greater_focus_" + e.id,
                    () -> new MagicFocusItem(e, GREATER_FOCUS_BONUS, true, curio(Rarity.RARE).fireResistant())));
        }
    }

    public static final DeferredItem<MagicFocusItem> ARCANE_FOCUS = ITEMS.register("arcane_focus",
            () -> new MagicFocusItem(null, ARCANE_FOCUS_BONUS, false, curio(Rarity.UNCOMMON)));

    /** +60 de mana máximo. */
    public static final DeferredItem<AttributeCurioItem> MANA_RING = ITEMS.register("mana_ring",
            () -> new AttributeCurioItem(curio(Rarity.UNCOMMON), CoreRegistries.MAX_MANA, 60.0,
                    AttributeModifier.Operation.ADD_VALUE, SoundEvents.ARMOR_EQUIP_GOLD.value(), Element.ARCANE));
    /** +30% de regeneração de mana. */
    public static final DeferredItem<AttributeCurioItem> REGEN_RING = ITEMS.register("regen_ring",
            () -> new AttributeCurioItem(curio(Rarity.UNCOMMON), CoreRegistries.MANA_REGEN, 0.30,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE, SoundEvents.ARMOR_EQUIP_GOLD.value(), Element.NATURE));
    /** +10% de poder mágico (todas as magias). */
    public static final DeferredItem<AttributeCurioItem> ARCANE_AMULET = ITEMS.register("arcane_amulet",
            () -> new AttributeCurioItem(curio(Rarity.RARE), CoreRegistries.SPELL_POWER, 0.10,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE, SoundEvents.AMETHYST_BLOCK_CHIME, Element.ARCANE));
    public static final DeferredItem<MeditationCharmItem> MEDITATION_CHARM = ITEMS.register("meditation_charm",
            () -> new MeditationCharmItem(curio(Rarity.UNCOMMON)));

    private static Item.Properties curio(Rarity rarity) {
        return new Item.Properties().stacksTo(1).rarity(rarity);
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener(EquipmentItems::fillTab);
    }

    private static void fillTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CoreRegistries.TAB_MAGIC.getKey()) return;
        for (Element e : Element.BASE) event.accept(FOCI.get(e).get());
        for (Element e : Element.BASE) event.accept(GREATER_FOCI.get(e).get());
        event.accept(ARCANE_FOCUS.get());
        event.accept(MANA_RING.get());
        event.accept(REGEN_RING.get());
        event.accept(ARCANE_AMULET.get());
        event.accept(MEDITATION_CHARM.get());
    }

    private EquipmentItems() {}
}
