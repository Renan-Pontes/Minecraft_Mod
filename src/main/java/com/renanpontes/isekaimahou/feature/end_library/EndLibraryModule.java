package com.renanpontes.isekaimahou.feature.end_library;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "end_library" — v1.6: a <b>Biblioteca do Vazio</b>, ruína-biblioteca sobre as ilhas externas do End
 * (estrutura própria que só nasce em terreno firme), com Escribas do Vazio e Fagulhas Estelares, baús que guardam
 * Tomos Selados (pergaminhos dos glifos espaciais do space_magic), e no Observatório do topo o chefe
 * <b>Devorador de Estrelas</b> (gravidade → meteoros → buraco negro). Material novo: <b>Fragmento Estelar</b>
 * (minério raro na pedra do End, drops) → Lingote Estelar → equipamento estelar (exige Círculo 7).
 * Tesouros de anime: Portão do Tesouro e Cajado do Rei Supremo. Assets: {@code tools/gen/end_library*.py}.
 */
public class EndLibraryModule implements Module {
    @Override
    public String id() {
        return "end_library";
    }

    @Override
    public void init(IEventBus modBus) {
        ElRegistries.register(modBus);
        ElCommands.register();
        ElEvents.register();
        modBus.addListener(this::onCreativeTab);
        registerResistances();
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            ElRegistries.eggs().forEach(egg -> event.accept(egg.get()));
        } else if (event.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            ElRegistries.worldItems().forEach(item -> event.accept(item.get()));
        } else if (event.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) {
            ElRegistries.magicItems().forEach(item -> event.accept(item.get()));
        }
    }

    /** Criaturas do vazio resistem ao arcano; a luz fere o que devora estrelas, a sombra apaga as fagulhas. */
    private static void registerResistances() {
        ElementReactions.setMultiplier(IsekaiMahou.id("star_devourer"), Element.SHADOW, 0.75F);
        ElementReactions.setMultiplier(IsekaiMahou.id("star_devourer"), Element.LIGHT, 1.35F);
        ElementReactions.setMultiplier(IsekaiMahou.id("star_devourer"), Element.FIRE, 0.75F);
        ElementReactions.setMultiplier(IsekaiMahou.id("void_scribe"), Element.SHADOW, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("void_scribe"), Element.LIGHT, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("star_wisp"), Element.LIGHT, 0.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("star_wisp"), Element.SHADOW, 1.5F);
        ElementReactions.setMultiplier(IsekaiMahou.id("star_wisp"), Element.WATER, 1.25F);
    }
}
