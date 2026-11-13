package com.renanpontes.isekaimahou.feature.towers;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.travel.guide.MapMarkers;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "towers" (v1.6) — <b>Torres de Desafio</b> no Overworld (variantes de pedra, gelo e areia):
 * <ul>
 *   <li>7 andares + pináculo; cada andar tem um {@code tower_floor_core} que roda o desafio (ondas, Salão das Runas com
 *       {@code tower_rune_switch}, câmara do tesouro, elite) e, ao vencer, some com os {@code tower_seal} da escada;</li>
 *   <li>no pináculo, o guardião: <b>Lorde Kobold do Pináculo</b> ({@code tower_kobold_lord}, 3 fases) ou um rei da
 *       região (Rei Wyvern no gelo, Basilisco do Deserto na areia), por id; o cofre do topo abre com a vitória;</li>
 *   <li>paredes protegidas até a conquista ({@link TowerData}); barreira contra quem chega voando ao pináculo;</li>
 *   <li>tesouros de anime (SAO): <b>Cristal de Teleporte</b> e <b>Lâminas Gêmeas</b> (Torrente Estelar, 16 golpes).</li>
 * </ul>
 * <p><b>v1.0 (release)</b>: torres com contrafortes, sacadas, vitrais, coroa e torreões com telhado; variantes novas
 * (floresta, submersa, flutuante); <b>Ilhas Voadoras</b> com ruínas e baús ({@link SkySites}: corrente ascendente);
 * <b>afixos</b> por andar ({@link TowerAffix}) e <b>recordes de tempo</b> ({@link TowerRecords}).
 * <p>Assets/dados/estruturas: {@code tools/gen/towers.py} (+ {@code towers_struct.py}, {@code towers_sky.py}).
 * Comandos: {@code /mahou towers ...}.
 */
public class TowersModule implements Module {
    @Override
    public String id() {
        return "towers";
    }

    @Override
    public void init(IEventBus modBus) {
        TwRegistries.register(modBus);
        TwEvents.register();
        TwCommands.register();
        // Pontos no minimapa (as três torres antigas já estão na lista do módulo de viagem)
        for (String tower : new String[] {"tower_forest", "tower_sunken", "tower_floating"}) MapMarkers.registerStructure(tower, "tower");
        // Ilhas Voadoras: ids com "tower" (o módulo ambience toca a música de torre pelo nome da estrutura)
        for (String isle : new String[] {"tower_sky_island_shrine", "tower_sky_island_ruins", "tower_sky_island_keep"}) {
            MapMarkers.registerStructure(isle, "tower");
        }
        modBus.addListener(this::onCreativeTab);
        // Kobold: pelo grosso (aguenta vento), mas o fogo e a luz o incomodam.
        ElementReactions.setMultiplier(IsekaiMahou.id("tower_kobold_lord"), Element.WIND, 0.75F);
        ElementReactions.setMultiplier(IsekaiMahou.id("tower_kobold_lord"), Element.FIRE, 1.25F);
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            event.accept(TwRegistries.KOBOLD_LORD_EGG.get());
        } else if (event.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            TwRegistries.worldItems().forEach(item -> event.accept(item.get()));
        } else if (event.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) {
            TwRegistries.gearItems().forEach(item -> event.accept(item.get()));
        }
    }
}
