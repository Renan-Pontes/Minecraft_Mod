package com.renanpontes.isekaimahou.feature.talents.client;

import com.renanpontes.isekaimahou.client.menu.MenuReveal;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.talents.Talents;
import com.renanpontes.isekaimahou.feature.talents.TalentsNetwork;
import com.renanpontes.isekaimahou.module.ClientModule;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "talents": a aba "Talentos" (menu B → Personagem), que aparece com o 1º ponto (nível 2) e
 * traz a árvore e, dentro dela, a escolha da especialização — uma aba só, para não lotar o menu.
 */
public class TalentsClient implements ClientModule {
    public static final String TAB = "talents";

    @Override
    public void initClient(IEventBus modBus) {
        TalentsNetwork.onSync = ClientTalents::onSync;
        Talents.clientSubclass = ClientTalents::subclass;
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientTalents.reset());

        MenuTabs.register(TAB, 11, Component.translatable("screen.isekai_mahou.talents.tab"), TalentsTab::new);
        MenuTabs.setCategory(TAB, MenuTabs.Category.CHARACTER);
        MenuTabs.setVisibility(TAB, () -> ClientMagic.data().path() != MagicPath.NONE);
        MenuReveal.addRule(TAB, d -> d.level() >= 2);
    }
}
