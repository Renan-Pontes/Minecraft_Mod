package com.renanpontes.isekaimahou.feature.ritual_mage;

import com.renanpontes.isekaimahou.feature.ritual_mage.network.MagePayloads;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "ritual_mage": Ritual do Mago — Pesquisa Arcana.
 * <ul>
 *   <li>Lente Arcana: observa fenômenos (data/isekai_mahou/mage_phenomena) e dá Notas de Pesquisa.</li>
 *   <li>Atlas Arcano: tela com os fenômenos descobertos e as teses.</li>
 *   <li>Mesa de Pesquisa: minijogo de circuito rúnico 5×5 que conclui as 10 Teses (XP, Runas, despertar).</li>
 * </ul>
 */
public class RitualMageModule implements Module {
    @Override
    public String id() {
        return "ritual_mage";
    }

    @Override
    public void init(IEventBus modBus) {
        MageRegistries.register(modBus);
        modBus.addListener(MagePayloads::register);
        MageEvents.register(modBus);
        MageCommands.register();
    }
}
