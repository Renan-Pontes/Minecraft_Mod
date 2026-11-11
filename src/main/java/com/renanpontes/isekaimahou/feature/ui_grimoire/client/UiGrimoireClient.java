package com.renanpontes.isekaimahou.feature.ui_grimoire.client;

import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;

/** Parte de cliente do modulo "ui_grimoire": registra as abas Grimorio, Loadouts e Glifos no menu B. */
public class UiGrimoireClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        MenuTabs.register("grimoire", 20, Component.translatable("screen.isekai_mahou.tab.grimoire"), GrimoireTab::new);
        MenuTabs.register("loadouts", 30, Component.translatable("screen.isekai_mahou.tab.loadouts"), LoadoutsTab::new);
        MenuTabs.register("glyphs", 40, Component.translatable("screen.isekai_mahou.tab.glyphs"), GlyphsTab::new);
    }
}
