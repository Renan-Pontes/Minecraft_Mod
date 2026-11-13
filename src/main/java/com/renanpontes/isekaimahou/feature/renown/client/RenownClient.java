package com.renanpontes.isekaimahou.feature.renown.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.feature.renown.RenownNetwork;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "renown": a aba "Renome" do menu B (Ranking / Títulos / Recordes), o título acima do nome
 * dos outros jogadores e o cronômetro da luta de chefe.
 */
public class RenownClient implements ClientModule {
    /** Id da aba (o ícone é {@code textures/gui/tab/renown.png}). */
    public static final String TAB = "renown";

    @Override
    public void initClient(IEventBus modBus) {
        RenownNetwork.clientBoard = b -> {
            RenownClientState.board = b;
            // Clique no Quadro de Renome: abre a aba já no Ranking
            if (b.open()) {
                RenownTab.section = RenownTab.RANKING;
                StatusScreen.open(TAB);
            }
        };
        RenownNetwork.clientTitles = t -> RenownClientState.titles = t;
        RenownNetwork.clientTitleSync = RenownClientState::onTitleSync;
        RenownNetwork.clientTimer = FightTimerHud::onPayload;

        MenuTabs.register(TAB, 65, Component.translatable("tab.isekai_mahou.renown"), RenownTab::new);
        MenuTabs.setCategory(TAB, MenuTabs.Category.SOCIAL);

        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, IsekaiMahou.id("renown_fight_timer"), FightTimerHud::render));

        IEventBus bus = NeoForge.EVENT_BUS;
        // LOW: depois das marcas de grupo/guerra do núcleo (elas mexem só no texto do nome; o título é uma linha à parte)
        bus.addListener(EventPriority.LOW, (RenderNameTagEvent e) -> TitleTags.onNameTag(e));
        bus.addListener((ClientTickEvent.Post e) -> FightTimerHud.tick());
        // receiveCanceled: outra interface pode esconder a barra, mas o lugar dela continua valendo para o relógio
        bus.addListener(EventPriority.LOWEST, true, (CustomizeGuiOverlayEvent.BossEventProgress e) -> FightTimerHud.onBossBar(e));
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            RenownClientState.clear();
            FightTimerHud.clear();
        });
    }
}
