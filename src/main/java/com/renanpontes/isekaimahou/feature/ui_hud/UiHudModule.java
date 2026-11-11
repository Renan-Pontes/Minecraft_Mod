package com.renanpontes.isekaimahou.feature.ui_hud;

import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "ui_hud": HUD completo e aba Status (Janela de Status).
 * Tudo é visual e lê apenas a cópia de dados do cliente ({@code ClientMagic}): não há registros,
 * pacotes nem lógica de servidor. A parte de cliente fica em {@code client/UiHudClient}.
 */
public class UiHudModule implements Module {
    @Override
    public String id() {
        return "ui_hud";
    }

    @Override
    public void init(IEventBus modBus) {
        // Sem conteúdo do lado comum: o HUD e a Janela de Status são registrados em UiHudClient.initClient.
    }
}
