package com.renanpontes.isekaimahou.feature.arcana_items.client;

import com.renanpontes.isekaimahou.feature.arcana_items.ArcanaHooks;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;

/** Parte de cliente do módulo "arcana_items": a tela de gravar o Pergaminho de Receita. */
public class ArcanaItemsClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        ArcanaHooks.openInscribe = InscribeScreen::open;
    }
}
