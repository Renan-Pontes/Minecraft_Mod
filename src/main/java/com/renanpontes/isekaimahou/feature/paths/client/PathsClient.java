package com.renanpontes.isekaimahou.feature.paths.client;

import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;

/** Parte de cliente do módulo "paths": aba "Caminhos" (ordem 10) no menu B e a linha "próxima Provação" do rastreador. */
public class PathsClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        MenuTabs.register("paths", 10, Component.translatable("screen.isekai_mahou.tab.paths"), PathsTab::new);
        PathsObjectives.register();
    }
}
