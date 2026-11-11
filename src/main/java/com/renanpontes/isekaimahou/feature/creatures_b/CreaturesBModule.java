package com.renanpontes.isekaimahou.feature.creatures_b;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.creatures_b.entity.CbBossRegistry;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "creatures_b": humanoides — Kobold, Orc, Orc Berserker, Lorde Orc (chefe), Ogro, Troll, Homem-Lagarto,
 * Minotauro (chefe), Lobisomem, Cultista (conjura glifos) e o Mago Errante (NPC comerciante).
 */
public class CreaturesBModule implements Module {
    @Override
    public String id() {
        return "creatures_b";
    }

    @Override
    public void init(IEventBus modBus) {
        CbRegistries.register(modBus);
        CbBossRegistry.register(modBus);
        WanderingMageSpawner.register();
        registerResistances();
    }

    /** Resistências/fraquezas a magias (0 = imune, 0.5 resiste, 2 fraco). */
    private static void registerResistances() {
        res("kobold", Element.EARTH, 0.75F);
        res("kobold", Element.WIND, 1.25F);
        res("orc", Element.SHADOW, 0.85F);
        res("orc_berserker", Element.SHADOW, 0.85F);
        res("orc_berserker", Element.FIRE, 0.8F);
        res("orc_lord", Element.EARTH, 0.75F);
        res("orc_lord", Element.SHADOW, 0.7F);
        res("ogre", Element.EARTH, 0.5F);
        res("ogre", Element.WIND, 1.25F);
        // Troll: regenera, mas o fogo dói em dobro e corta a regeneração.
        res("troll", Element.FIRE, 2.0F);
        res("troll", Element.EARTH, 0.75F);
        res("lizardman", Element.WATER, 0.5F);
        res("lizardman", Element.FIRE, 1.25F);
        res("minotaur", Element.EARTH, 0.5F);
        res("minotaur", Element.FIRE, 0.75F);
        res("minotaur", Element.WIND, 1.25F);
        res("werewolf", Element.LIGHT, 1.75F);
        res("werewolf", Element.SHADOW, 0.5F);
        res("cultist", Element.SHADOW, 0.5F);
        res("cultist", Element.LIGHT, 1.5F);
        res("wandering_mage", Element.LIGHT, 0.75F);
    }

    private static void res(String entity, Element base, float mult) {
        ElementReactions.setMultiplier(IsekaiMahou.id(entity), base, mult);
    }
}
