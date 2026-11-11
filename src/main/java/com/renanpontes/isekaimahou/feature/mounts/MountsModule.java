package com.renanpontes.isekaimahou.feature.mounts;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.mounts.entity.Griffin;
import com.renanpontes.isekaimahou.feature.mounts.entity.Pegasus;
import com.renanpontes.isekaimahou.feature.mounts.entity.SkyRay;
import com.renanpontes.isekaimahou.feature.mounts.entity.Wyvern;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Modulo "mounts": montarias voadoras — Grifo (montanhas do Overworld), Arraia do Céu (Plano Elemental),
 * Pégaso (Santuário Celestial) e Wyvern (Abismo). Todas usam a sela vanilla; pulo sobe, agachar desce
 * e a tecla do módulo (padrão R) usa a habilidade da montaria. Apito de Montaria chama as suas.
 */
public class MountsModule implements Module {
    @Override
    public String id() {
        return "mounts";
    }

    @Override
    public void init(IEventBus modBus) {
        MountsRegistries.register(modBus);
        modBus.addListener(MountsModule::attributes);
        modBus.addListener(MountsModule::creativeTab);
        modBus.addListener(MountsSpawns::register);
        modBus.addListener(MountsNetwork::register);

        // Resistências e fraquezas elementais (magia)
        ElementReactions.setMultiplier(IsekaiMahou.id(Griffin.ID), Element.WIND, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id(Griffin.ID), Element.EARTH, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id(SkyRay.ID), Element.WIND, 0.25F);
        ElementReactions.setMultiplier(IsekaiMahou.id(SkyRay.ID), Element.WATER, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id(SkyRay.ID), Element.EARTH, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id(Pegasus.ID), Element.LIGHT, 0.25F);
        ElementReactions.setMultiplier(IsekaiMahou.id(Pegasus.ID), Element.SHADOW, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id(Wyvern.ID), Element.SHADOW, 0.0F);
        ElementReactions.setMultiplier(IsekaiMahou.id(Wyvern.ID), Element.LIGHT, 2.0F);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(MountsRegistries.GRIFFIN.get(), Griffin.createAttributes().build());
        e.put(MountsRegistries.SKY_RAY.get(), SkyRay.createAttributes().build());
        e.put(MountsRegistries.PEGASUS.get(), Pegasus.createAttributes().build());
        e.put(MountsRegistries.WYVERN.get(), Wyvern.createAttributes().build());
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            MountsRegistries.tabItems().forEach(i -> e.accept(i.get()));
        }
    }
}
