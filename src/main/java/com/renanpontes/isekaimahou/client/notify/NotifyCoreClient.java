package com.renanpontes.isekaimahou.client.notify;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.ContainerTab;
import com.renanpontes.isekaimahou.client.menu.MenuReveal;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.network.ClientHooks;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Cliente do núcleo v1.1 — menos poluição na tela: filtro do chat ({@link ChatRouter}), registro de avisos
 * ({@link NoticeLog}) e a aba "Avisos" ({@link NoticesTab}), o "Guia de Viagem" ({@link ContainerTab#GUIDE}), a revelação
 * progressiva do menu ({@link MenuReveal}) e o filtro das faixas de sistema repetidas.
 */
public final class NotifyCoreClient {
    /** Faixa de sistema igual a esta, dentro deste tempo, não aparece de novo. */
    private static final long SYSTEM_REPEAT_MS = 5000L;
    private static String lastSystem = "";
    private static long lastSystemAt;

    private NotifyCoreClient() {}

    public static void init() {
        MenuTabs.register("notices", 900, Component.translatable("screen.isekai_mahou.tab.notices"), NoticesTab::new);
        MenuTabs.register(ContainerTab.GUIDE, 0, Component.translatable("screen.isekai_mahou.tab.guide"),
                () -> new ContainerTab(ContainerTab.GUIDE, "journal"));
        MenuTabs.setIcon(ContainerTab.GUIDE, IsekaiMahou.id("textures/gui/tab/guide.png"));
        MenuTabs.setIcon("notices", IsekaiMahou.id("textures/gui/tab/notices.png"));

        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, ChatRouter::onChat);
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered e) -> ChatRouter.onInteract(e));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            if (Minecraft.getInstance().level == null) return;
            ChatRouter.tick();
            MenuReveal.tick();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            ChatRouter.clear();
            MenuReveal.logout();
            NoticeLog.clear();
            lastSystem = "";
        });
    }

    /**
     * Depois que todos os módulos instalaram seus ganchos: faixas de sistema ({@code Rewards.banner}) repetidas em até
     * 5 s são descartadas, e as que passam ficam no registro de avisos. Encadeia o gancho atual (cinematic).
     */
    public static void afterModules() {
        Consumer<CorePayloads.AnnounceText> previous = ClientHooks.announce;
        ClientHooks.announce = p -> {
            if (p.style() == Announce.STYLE_SYSTEM) {
                String text = p.text().getString();
                long now = Util.getMillis();
                if (text.equals(lastSystem) && now - lastSystemAt < SYSTEM_REPEAT_MS) return;
                lastSystem = text;
                lastSystemAt = now;
                NoticeLog.add(p.text(), NoticeLog.Kind.INFO);
            }
            previous.accept(p);
        };
    }
}
