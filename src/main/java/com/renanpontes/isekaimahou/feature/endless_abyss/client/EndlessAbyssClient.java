package com.renanpontes.isekaimahou.feature.endless_abyss.client;

import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.feature.endless_abyss.AbyssManager;
import com.renanpontes.isekaimahou.feature.endless_abyss.AbyssNetwork;
import com.renanpontes.isekaimahou.module.ClientModule;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "endless_abyss": a aba "Abismo" do menu B (escondida até o nível 100 — "entra nela pelo B
 * após o lvl 100") e o visual escuro das Sombras.
 */
public class EndlessAbyssClient implements ClientModule {
    /** Id da aba (o ícone é {@code textures/gui/tab/endless_abyss.png}). */
    public static final String TAB = "endless_abyss";

    @Override
    public void initClient(IEventBus modBus) {
        AbyssNetwork.clientInfo = i -> AbyssClientState.info = i;
        MenuTabs.register(TAB, 85, Component.translatable("tab.isekai_mahou.endless_abyss"), AbyssTab::new);
        MenuTabs.setCategory(TAB, MenuTabs.Category.ADVENTURE);
        MenuTabs.setVisibility(TAB, () -> ClientMagic.data().level() >= AbyssManager.UNLOCK_LEVEL);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> AbyssClientState.info = AbyssNetwork.Info.EMPTY);
        ShadowTint.register();
    }
}
