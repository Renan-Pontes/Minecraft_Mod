package com.renanpontes.isekaimahou.feature.nether_citadel;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Módulo "nether_citadel" (v1.6) — motivo para ir ao Nether:
 * <ul>
 *   <li><b>Cidadela Demoníaca</b> ({@code nether_citadel}): fortaleza com muralha, pátio, Forja Infernal, santuário do
 *       Altar do Pacto, quartel e salão do trono com o <b>Lorde Demônio</b> ({@code demon_lord}, 4 fases);</li>
 *   <li>guarnição: <b>Cavaleiro Infernal</b> ({@code demon_knight}) e <b>Súcubo Feiticeira</b> ({@code demon_succubus});</li>
 *   <li><b>Altar do Pacto</b> (poder com preço) e <b>Forja Infernal</b> (infusão de fogo/sombra em armas);</li>
 *   <li><b>Adamantita</b>: minério raro no Nether, lingote, ferramentas e armadura (≈ netherite+, nunca obrigatória);</li>
 *   <li>tesouros de anime: <b>Lâmina Antimagia</b> e <b>Katana do Sol</b>.</li>
 * </ul>
 * Assets/dados/estruturas: {@code tools/gen/nether_citadel.py}. Comandos: {@code /mahou nether_citadel ...}.
 */
public class NetherCitadelModule implements Module {
    @Override
    public String id() {
        return "nether_citadel";
    }

    @Override
    public void init(IEventBus modBus) {
        NcRegistries.register(modBus);
        NcEvents.register();
        NcCommands.register();
        modBus.addListener(this::onCreativeTab);
        registerResistances();
    }

    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CoreRegistries.TAB_CREATURES.getKey()) {
            NcRegistries.eggs().forEach(egg -> event.accept(egg.get()));
        } else if (event.getTabKey() == CoreRegistries.TAB_WORLD.getKey()) {
            NcRegistries.worldItems().forEach(item -> event.accept(item.get()));
        } else if (event.getTabKey() == CoreRegistries.TAB_MAGIC.getKey()) {
            NcRegistries.gearItems().forEach(item -> event.accept(item.get()));
        }
    }

    /** Demônios do Nether: imunes a fogo, resistem às trevas, a luz e a água os ferem mais. */
    private static void registerResistances() {
        for (String id : new String[] {"demon_lord", "demon_knight", "demon_succubus"}) {
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.FIRE, 0.0F);
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.SHADOW, 0.6F);
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.LIGHT, 1.5F);
            ElementReactions.setMultiplier(IsekaiMahou.id(id), Element.WATER, 1.25F);
        }
    }
}
