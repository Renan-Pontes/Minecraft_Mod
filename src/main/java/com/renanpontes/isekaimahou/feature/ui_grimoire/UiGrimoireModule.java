package com.renanpontes.isekaimahou.feature.ui_grimoire;

import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Modulo "ui_grimoire": abas Grimório (editor de magias com Detalhamento e Presets), Loadouts (9 loadouts fixos) e
 * Coleção de Glifos do menu B.
 *
 * Todo o módulo é interface de CLIENTE (pacote {@code client}): as alterações chegam ao servidor pelos pacotes do
 * núcleo (SetLoadoutSlot, SelectLoadout, PresetEdit, LibraryEdit), que já validam e salvam os dados. Por isso o
 * lado comum não registra nada — e o servidor dedicado nunca carrega classes de tela.
 */
public class UiGrimoireModule implements Module {
    @Override
    public String id() {
        return "ui_grimoire";
    }

    @Override
    public void init(IEventBus modBus) {
        // Sem registros no lado comum: ver o comentário da classe.
    }
}
