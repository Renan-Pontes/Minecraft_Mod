package com.renanpontes.isekaimahou.feature.creatures_e;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.abyss.VoidCrawler;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.abyss.WatcherEye;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.abyss.YoungKraken;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.celestial.AngelSentinel;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.celestial.Cherub;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.celestial.CrystalGolem;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental.GreaterAirElemental;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental.GreaterEarthElemental;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental.GreaterFireElemental;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental.GreaterWaterElemental;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental.LesserPhoenix;
import com.renanpontes.isekaimahou.feature.creatures_e.entity.elemental.StormWyvern;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Modulo "creatures_e": criaturas das dimensoes do mod.
 * <ul>
 *   <li>Plano Elemental: Elementais Maiores (fogo, agua, terra, ar), Fenix Menor (renasce uma vez), Wyvern de Tempestade.</li>
 *   <li>Abismo: Rastejador do Vazio, Olho Vigia, Kraken Jovem.</li>
 *   <li>Santuario Celestial: Anjo Sentinela, Querubim (cura os celestiais), Golem de Cristal (reflete projeteis).</li>
 * </ul>
 * Spawns so nos biomas das dimensoes (biome modifiers em data/isekai_mahou/neoforge/biome_modifier/creatures_e_*).
 */
public class CreaturesEModule implements Module {
    @Override
    public String id() {
        return "creatures_e";
    }

    @Override
    public void init(IEventBus modBus) {
        CeRegistries.register(modBus);
        modBus.addListener(this::attributes);
        modBus.addListener(CeSpawns::register);
        modBus.addListener(this::creativeTab);
        resistances();
    }

    private void attributes(EntityAttributeCreationEvent e) {
        e.put(CeRegistries.GREATER_FIRE_ELEMENTAL.get(), GreaterFireElemental.createAttributes().build());
        e.put(CeRegistries.GREATER_WATER_ELEMENTAL.get(), GreaterWaterElemental.createAttributes().build());
        e.put(CeRegistries.GREATER_EARTH_ELEMENTAL.get(), GreaterEarthElemental.createAttributes().build());
        e.put(CeRegistries.GREATER_AIR_ELEMENTAL.get(), GreaterAirElemental.createAttributes().build());
        e.put(CeRegistries.LESSER_PHOENIX.get(), LesserPhoenix.createAttributes().build());
        e.put(CeRegistries.STORM_WYVERN.get(), StormWyvern.createAttributes().build());
        e.put(CeRegistries.VOID_CRAWLER.get(), VoidCrawler.createAttributes().build());
        e.put(CeRegistries.WATCHER_EYE.get(), WatcherEye.createAttributes().build());
        e.put(CeRegistries.YOUNG_KRAKEN.get(), YoungKraken.createAttributes().build());
        e.put(CeRegistries.ANGEL_SENTINEL.get(), AngelSentinel.createAttributes().build());
        e.put(CeRegistries.CHERUB.get(), Cherub.createAttributes().build());
        e.put(CeRegistries.CRYSTAL_GOLEM.get(), CrystalGolem.createAttributes().build());
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            CeRegistries.TAB_ITEMS.forEach(i -> e.accept(i.get()));
        }
    }

    /** Resistencias/fraquezas a magias (0 imune, &lt;1 resiste, &gt;1 fraco). Fusoes contam os dois pais. */
    private static void resistances() {
        res("greater_fire_elemental", Element.FIRE, 0F, Element.WATER, 1.75F, Element.EARTH, 0.75F);
        res("greater_water_elemental", Element.WATER, 0F, Element.FIRE, 0.5F, Element.EARTH, 1.5F);
        res("greater_earth_elemental", Element.EARTH, 0F, Element.WIND, 1.75F, Element.FIRE, 0.5F);
        res("greater_air_elemental", Element.WIND, 0F, Element.EARTH, 1.75F, Element.FIRE, 0.75F);
        res("lesser_phoenix", Element.FIRE, 0F, Element.WATER, 1.75F, Element.LIGHT, 0.5F);
        res("storm_wyvern", Element.WIND, 0.25F, Element.FIRE, 0.5F, Element.EARTH, 1.75F);
        res("void_crawler", Element.SHADOW, 0F, Element.LIGHT, 2.0F);
        res("watcher_eye", Element.SHADOW, 0F, Element.LIGHT, 2.0F, Element.WIND, 0.75F);
        res("young_kraken", Element.WATER, 0.25F, Element.SHADOW, 0.5F, Element.LIGHT, 1.5F, Element.FIRE, 0.75F);
        res("angel_sentinel", Element.LIGHT, 0F, Element.SHADOW, 1.75F);
        res("cherub", Element.LIGHT, 0F, Element.SHADOW, 1.5F);
        res("crystal_golem", Element.LIGHT, 0.25F, Element.EARTH, 0.5F, Element.SHADOW, 1.5F);
    }

    /** {@code pairs} = elemento, multiplicador, elemento, multiplicador... */
    private static void res(String entity, Object... pairs) {
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            ElementReactions.setMultiplier(IsekaiMahou.id(entity), (Element) pairs[i], (Float) pairs[i + 1]);
        }
    }
}
