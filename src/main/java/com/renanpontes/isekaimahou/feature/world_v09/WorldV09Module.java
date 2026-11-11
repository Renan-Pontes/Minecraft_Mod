package com.renanpontes.isekaimahou.feature.world_v09;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "world_v09" (v0.9, docs/V09_DESIGN.md §5): estruturas novas do Overworld e o Veio de Mana.
 * <ul>
 *   <li>Covis com o chefe no molde: {@code hydra_lair}, {@code wyvern_peak}, {@code basilisk_tomb}, {@code colossus_ruins}
 *       (tag {@code #isekai_mahou:boss_lair});</li>
 *   <li>portais ADORMECIDOS prontos (blocos do dimensions): {@code elemental_gate}, {@code abyssal_chasm},
 *       {@code celestial_stairway}, {@code twilight_rupture} (tag {@code #isekai_mahou:portal_site});</li>
 *   <li>{@code ascension_sanctum}: Altar de Ascensão público pronto ({@code {PublicAltar:1b}}), zona segura;</li>
 *   <li>Pedra de Teleporte ({@code isekai_mahou:travel_stone}) em todas; cada uma vira um lugar ({@link V09Places});</li>
 *   <li>Veio de Mana ({@code mana_vein_ore}/{@code deepslate_mana_vein_ore}) e Aglomerados de Cristal de Mana.</li>
 * </ul>
 * Dados (moldes .nbt, worldgen, loot, tags, texturas, traduções) gerados por {@code tools/gen/world_v09.py}.
 * Todas usam {@code isekai_mahou:random_spread_multi} longe das cidades (10 chunks), capitais (13) e vilas (4).
 * <p>Como achar (cheats): {@code /mahou sites} lista a mais próxima de cada uma (clique = /tp), ou
 * <pre>
 * /locate structure isekai_mahou:hydra_lair          (pântano / mangue)
 * /locate structure isekai_mahou:wyvern_peak         (picos e encostas nevadas)
 * /locate structure isekai_mahou:basilisk_tomb       (deserto / badlands)
 * /locate structure isekai_mahou:colossus_ruins      (planície, floresta, prado, savana)
 * /locate structure isekai_mahou:elemental_gate      (planície, floresta, taiga, savana, cerejeiras)
 * /locate structure isekai_mahou:abyssal_chasm       (floresta escura, badlands, taiga, planície, colinas)
 * /locate structure isekai_mahou:celestial_stairway  (prado, cerejeiras, bosque, encostas, colinas)
 * /locate structure isekai_mahou:twilight_rupture    (oceanos, menos os congelados)
 * /locate structure isekai_mahou:ascension_sanctum   (planície, floresta, taiga, savana, cerejeiras...)
 * /locate structure #isekai_mahou:boss_lair · #isekai_mahou:portal_site · #isekai_mahou:world_v09_sites
 * </pre>
 * Cada estrutura também tem a sua tag ({@code #isekai_mahou:<id>}) para o Diário localizar.
 */
public class WorldV09Module implements Module {
    @Override
    public String id() {
        return "world_v09";
    }

    @Override
    public void init(IEventBus modBus) {
        V09Registry.register(modBus);
        modBus.addListener(WorldV09Module::creativeTabs);
        V09Places.init();
        V09Commands.init();
    }

    private static void creativeTabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            e.accept(V09Registry.MANA_VEIN_ORE_ITEM.get());
            e.accept(V09Registry.DEEPSLATE_MANA_VEIN_ORE_ITEM.get());
            e.accept(V09Registry.MANA_CRYSTAL_CLUSTER_ITEM.get());
        }
    }
}
