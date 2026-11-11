package com.renanpontes.isekaimahou.feature.creatures_d;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.ArcaneGolem;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.CreepingShade;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.CrystalMole;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.EmberImp;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.LumenSprite;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.MossGolem;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.Salamander;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.SolarSentinel;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.StormHawk;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.Sylph;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.TideSerpent;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.Undine;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.VoidBat;
import com.renanpontes.isekaimahou.feature.creatures_d.entity.WanderingSpirit;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

/**
 * Módulo "creatures_d": Elementais do Overworld (14 criaturas GeckoLib) — a principal fonte de
 * Essências para a Ascensão — mais o Espírito Errante e o Golem Arcano.
 * Spawns por bioma: data/isekai_mahou/neoforge/biome_modifier; drops: loot_table/entities.
 */
public class CreaturesDModule implements Module {
    @Override
    public String id() {
        return "creatures_d";
    }

    @Override
    public void init(IEventBus modBus) {
        CdEntities.ENTITIES.register(modBus);
        CdEntities.ITEMS.register(modBus);
        modBus.addListener(this::attributes);
        modBus.addListener((RegisterSpawnPlacementsEvent e) -> CdSpawns.register(e));
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) CdEntities.EGGS.forEach(egg -> e.accept(egg.get()));
        });
        elementalAffinities();
    }

    private void attributes(EntityAttributeCreationEvent e) {
        e.put(CdEntities.SALAMANDER.get(), Salamander.createAttributes().build());
        e.put(CdEntities.EMBER_IMP.get(), EmberImp.createAttributes().build());
        e.put(CdEntities.UNDINE.get(), Undine.createAttributes().build());
        e.put(CdEntities.TIDE_SERPENT.get(), TideSerpent.createAttributes().build());
        e.put(CdEntities.MOSS_GOLEM.get(), MossGolem.createAttributes().build());
        e.put(CdEntities.CRYSTAL_MOLE.get(), CrystalMole.createAttributes().build());
        e.put(CdEntities.SYLPH.get(), Sylph.createAttributes().build());
        e.put(CdEntities.STORM_HAWK.get(), StormHawk.createAttributes().build());
        e.put(CdEntities.LUMEN_SPRITE.get(), LumenSprite.createAttributes().build());
        e.put(CdEntities.SOLAR_SENTINEL.get(), SolarSentinel.createAttributes().build());
        e.put(CdEntities.CREEPING_SHADE.get(), CreepingShade.createAttributes().build());
        e.put(CdEntities.VOID_BAT.get(), VoidBat.createAttributes().build());
        e.put(CdEntities.WANDERING_SPIRIT.get(), WanderingSpirit.createAttributes().build());
        e.put(CdEntities.ARCANE_GOLEM.get(), ArcaneGolem.createAttributes().build());
    }

    /**
     * Resistências/fraquezas às magias: cada elemental é imune ao próprio elemento e fraco ao oposto
     * (Fogo x Água, Terra x Vento, Luz x Sombra). Fusões contam os dois pais (ex.: Raio = Fogo + Vento).
     */
    private static void elementalAffinities() {
        opposed("salamander", Element.FIRE, Element.WATER, 2.0F);
        opposed("ember_imp", Element.FIRE, Element.WATER, 2.0F);
        opposed("undine", Element.WATER, Element.FIRE, 1.75F);
        opposed("tide_serpent", Element.WATER, Element.FIRE, 1.75F);
        opposed("moss_golem", Element.EARTH, Element.WIND, 1.75F);
        opposed("crystal_mole", Element.EARTH, Element.WIND, 1.75F);
        opposed("sylph", Element.WIND, Element.EARTH, 1.75F);
        opposed("storm_hawk", Element.WIND, Element.EARTH, 1.75F);
        opposed("lumen_sprite", Element.LIGHT, Element.SHADOW, 2.0F);
        opposed("solar_sentinel", Element.LIGHT, Element.SHADOW, 2.0F);
        opposed("creeping_shade", Element.SHADOW, Element.LIGHT, 2.0F);
        opposed("void_bat", Element.SHADOW, Element.LIGHT, 2.0F);
        // Golem de Musgo é uma planta: o fogo pega fácil
        ElementReactions.setMultiplier(IsekaiMahou.id("moss_golem"), Element.FIRE, 1.5F);
        // Espírito: a luz purifica, a sombra é o seu lar
        ElementReactions.setMultiplier(IsekaiMahou.id("wandering_spirit"), Element.LIGHT, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("wandering_spirit"), Element.SHADOW, 0.5F);
        // Golem Arcano: runas de proteção contra todos os elementos (o arcano puro o fere normalmente)
        for (Element e : Element.BASE) ElementReactions.setMultiplier(IsekaiMahou.id("arcane_golem"), e, 0.75F);
    }

    private static void opposed(String id, Element own, Element weakness, float mult) {
        ElementReactions.setMultiplier(IsekaiMahou.id(id), own, 0.0F);
        ElementReactions.setMultiplier(IsekaiMahou.id(id), weakness, mult);
    }
}
