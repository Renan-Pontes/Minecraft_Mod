package com.renanpontes.isekaimahou.feature.journal.client;

import com.renanpontes.isekaimahou.client.menu.ContainerTab;
import com.renanpontes.isekaimahou.client.menu.MenuReveal;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.feature.journal.JournalContent;
import com.renanpontes.isekaimahou.feature.journal.JournalTargets;
import com.renanpontes.isekaimahou.feature.journal.data.JournalBook;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "journal": renderer do Espírito Guia, as abas "Diário" (50) e "Bestiário" (70) do menu B,
 * o provedor do rastreador de objetivos (capítulo + passo atual, com seta quando há lugar) e o observador do tutorial.
 * v1.0 release: a seção "Onde Conseguir" ({@link WhereTab}) e o aviso das opções da campanha ({@link StoryClient}).
 */
public class JournalClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(JournalContent.GUIDE_SPIRIT.get(), GuideSpiritRenderer::new));
        MenuTabs.register("journal", 50, Component.translatable("screen.isekai_mahou.journal.tab"), JournalTab::new);
        MenuTabs.register("bestiary", 70, Component.translatable("screen.isekai_mahou.bestiary.tab"), BestiaryTab::new);
        // v1.0 release: "Onde Conseguir" — mais uma seção do Guia de Viagem (nenhuma aba nova na barra).
        MenuTabs.register(WhereTab.TAB_ID, 55, Component.translatable("screen.isekai_mahou.where.tab"), WhereTab::new);
        MenuTabs.setParent(WhereTab.TAB_ID, ContainerTab.GUIDE);
        // Menu enxuto no começo: a seção aparece quando o primeiro capítulo fecha (ou para quem já tem progresso).
        MenuReveal.addRule(WhereTab.TAB_ID, d -> d.hasFlag("journal.awakening") || d.level() >= 5 || d.circle() >= 2);
        NeoForge.EVENT_BUS.addListener(StoryClient::onTick);
        JournalObjectives.register();
        NeoForge.EVENT_BUS.addListener(TutorialWatcher::onTick);
        // Ao sair do mundo: esquece o livro do servidor anterior, o alvo do rastreador e as criaturas de exibição.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            JournalBook.clearClient();
            JournalTargets.clear();
            TutorialWatcher.reset();
            StoryClient.reset();
            EntityPreview.clear();
        });
    }
}
