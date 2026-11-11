package com.renanpontes.isekaimahou.feature.creatures_c;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Basilisk;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.DireWolf;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Fairy;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Gargoyle;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Harpy;
import com.renanpontes.isekaimahou.feature.creatures_c.entity.Mimic;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Modulo "creatures_c": Feras — Lobo Atroz (domável e montaria), Harpia, Basilisco, Mímico, Gárgula e Fada.
 * Spawns por biome modifier (data/isekai_mahou/neoforge/biome_modifier/creatures_c_*.json) + regras em {@link CcSpawns}.
 */
public class CreaturesCModule implements Module {
    @Override
    public String id() {
        return "creatures_c";
    }

    @Override
    public void init(IEventBus modBus) {
        CcRegistries.register(modBus);
        modBus.addListener(CreaturesCModule::attributes);
        modBus.addListener(CreaturesCModule::creativeTab);
        modBus.addListener(CcSpawns::register);
        NeoForge.EVENT_BUS.addListener(CreaturesCModule::brewing);

        // Resistências e fraquezas elementais (magia)
        ElementReactions.setMultiplier(IsekaiMahou.id("basilisk"), Element.LIGHT, 2.0F);
        ElementReactions.setMultiplier(IsekaiMahou.id("basilisk"), Element.EARTH, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("gargoyle"), Element.EARTH, 2.0F);
        ElementReactions.setMultiplier(IsekaiMahou.id("gargoyle"), Element.FIRE, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("gargoyle"), Element.WIND, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("harpy"), Element.WIND, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("harpy"), Element.EARTH, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("mimic"), Element.FIRE, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("fairy"), Element.LIGHT, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("fairy"), Element.SHADOW, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("dire_wolf"), Element.FIRE, 1.25F);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(CcRegistries.DIRE_WOLF.get(), DireWolf.createAttributes().build());
        e.put(CcRegistries.HARPY.get(), Harpy.createAttributes().build());
        e.put(CcRegistries.BASILISK.get(), Basilisk.createAttributes().build());
        e.put(CcRegistries.MIMIC.get(), Mimic.createAttributes().build());
        e.put(CcRegistries.GARGOYLE.get(), Gargoyle.createAttributes().build());
        e.put(CcRegistries.FAIRY.get(), Fairy.createAttributes().build());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            CcRegistries.tabItems().forEach(i -> e.accept(i.get()));
        }
    }

    /** Materiais das feras como ingredientes de poções (a partir da Poção Estranha). */
    private static void brewing(RegisterBrewingRecipesEvent e) {
        PotionBrewing.Builder b = e.getBuilder();
        b.addMix(Potions.AWKWARD, CcRegistries.HARPY_FEATHER.get(), Potions.SLOW_FALLING);
        b.addMix(Potions.AWKWARD, CcRegistries.BASILISK_SCALE.get(), Potions.SLOWNESS);
        b.addMix(Potions.AWKWARD, CcRegistries.GARGOYLE_FRAGMENT.get(), Potions.TURTLE_MASTER);
        b.addMix(Potions.AWKWARD, CcRegistries.MIMIC_TOOTH.get(), Potions.LUCK);
        b.addMix(Potions.AWKWARD, CcRegistries.FAIRY_DUST.get(), Potions.REGENERATION);
    }
}
