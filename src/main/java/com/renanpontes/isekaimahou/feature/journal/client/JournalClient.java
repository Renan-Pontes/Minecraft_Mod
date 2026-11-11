package com.renanpontes.isekaimahou.feature.journal.client;

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
 */
public class JournalClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(JournalContent.GUIDE_SPIRIT.get(), GuideSpiritRenderer::new));
        MenuTabs.register("journal", 50, Component.translatable("screen.isekai_mahou.journal.tab"), JournalTab::new);
        MenuTabs.register("bestiary", 70, Component.translatable("screen.isekai_mahou.bestiary.tab"), BestiaryTab::new);
        JournalObjectives.register();
        NeoForge.EVENT_BUS.addListener(TutorialWatcher::onTick);
        // Ao sair do mundo: esquece o livro do servidor anterior, o alvo do rastreador e as criaturas de exibição.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            JournalBook.clearClient();
            JournalTargets.clear();
            TutorialWatcher.reset();
            EntityPreview.clear();
        });
    }
}
