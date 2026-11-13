package com.renanpontes.isekaimahou.feature.gear_plus.client;

import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.gear_plus.GearNet;
import com.renanpontes.isekaimahou.module.ClientModule;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Parte de cliente do módulo "gear_plus": a aba "Equipamento" (menu B → Personagem) e as linhas extras nas dicas dos
 * itens (conjunto, gemas, Vínculo da arma). Nada no HUD.
 */
public class GearPlusClient implements ClientModule {
    public static final String TAB = "gear_plus";

    @Override
    public void initClient(IEventBus modBus) {
        GearNet.onSync = ClientGear::onSync;
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientGear.reset());
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent e) -> GearTooltips.onTooltip(e));

        MenuTabs.register(TAB, 12, Component.translatable("screen.isekai_mahou.gear_plus.tab"), GearTab::new);
        MenuTabs.setCategory(TAB, MenuTabs.Category.CHARACTER);
        MenuTabs.setVisibility(TAB, () -> ClientMagic.data().path() != MagicPath.NONE);
    }
}
