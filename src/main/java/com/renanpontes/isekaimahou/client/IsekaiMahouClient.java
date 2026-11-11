package com.renanpontes.isekaimahou.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.DefaultStatusTab;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.client.objectives.CoreObjectives;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveHud;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.network.ClientHooks;
import com.renanpontes.isekaimahou.network.ClientMagic;
import com.renanpontes.isekaimahou.network.CorePayloads;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/** Cliente do núcleo + inicialização das partes de cliente de todos os módulos. */
@Mod(value = IsekaiMahou.MODID, dist = Dist.CLIENT)
public class IsekaiMahouClient {
    private static final boolean[] slotDown = new boolean[Progression.SLOTS];

    public IsekaiMahouClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(IsekaiMahouClient::keys);
        modBus.addListener(IsekaiMahouClient::layers);
        modBus.addListener(IsekaiMahouClient::renderers);
        // GlyphArt guarda "textura existe?" em cache: esquece tudo a cada recarga de recursos (F3+T, pacotes)
        modBus.addListener((RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((ResourceManagerReloadListener) rm -> GlyphArt.clearCache()));
        NeoForge.EVENT_BUS.addListener(IsekaiMahouClient::tick);
        // v1.0: 9 loadouts — Alt/Mouse 5 + scroll e Alt + 1..9
        LoadoutInput.init();
        NeoForge.EVENT_BUS.addListener(IsekaiMahouClient::logout);
        // Teclas de outros mods (Xaero, JEI...) iguais às do mod são movidas uma vez para teclas livres
        NeoForge.EVENT_BUS.addListener(KeyConflictFix::onLogin);
        // Minimapa do Xaero no canto superior direito (o instalador deixa assim): o rastreador de objetivos desce
        // para baixo dele (mapa ≈ 1/4 da altura da tela + coordenadas)
        if (net.neoforged.fml.ModList.get().isLoaded("xaerominimap")) {
            com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker.reserveTop("xaero_minimap", () -> {
                int gh = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
                return Math.max(0, (int) Math.ceil(gh * 0.335) - com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker.TOP);
            });
        }

        MenuTabs.register("status", 0, Component.translatable("screen.isekai_mahou.tab.status"), DefaultStatusTab::new);

        // Padrões dos ganchos (o módulo "cinematic" substitui por versões completas)
        ClientHooks.announce = p -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setOverlayMessage(p.text().copy().withColor(p.rgb()), false);
        };
        ClientHooks.cutscene = p -> Minecraft.getInstance().gui.setTitle(p.title().copy().withColor(p.rgb()));
        ClientHooks.toast = p -> Minecraft.getInstance().getToasts().addToast(new RewardToast(p.title(), p.subtitle(), p.icon(), p.rgb()));
        CoreObjectives.register();

        com.renanpontes.isekaimahou.client.boss.BossToolkitClient.init(modBus);
        com.renanpontes.isekaimahou.client.status.StatusCoreClient.init(modBus);
        com.renanpontes.isekaimahou.client.stack.StackCoreClient.init(modBus);
        com.renanpontes.isekaimahou.client.reputation.ReputationCoreClient.init(modBus);
        com.renanpontes.isekaimahou.client.classes.ClassesCoreClient.init(modBus);
        com.renanpontes.isekaimahou.client.onboard.OnboardCoreClient.init(modBus);
        com.renanpontes.isekaimahou.client.social.SocialCoreClient.init(modBus);
        com.renanpontes.isekaimahou.client.ux.UxCoreClient.init(modBus);
        com.renanpontes.isekaimahou.client.spellfx.SpellFxClient.init(modBus); // efeitos visuais das magias
        com.renanpontes.isekaimahou.client.anim.MahouAnimsClient.init(modBus, container); // animações de combate
        // v1.1: filtro do chat, aba "Avisos", Guia de Viagem e menu progressivo
        com.renanpontes.isekaimahou.client.notify.NotifyCoreClient.init();
        ClientModules.initAll(modBus);
        com.renanpontes.isekaimahou.client.notify.NotifyCoreClient.afterModules();
    }

    private static void keys(RegisterKeyMappingsEvent e) {
        for (KeyMapping k : CoreKeys.SLOTS) e.register(k);
        e.register(CoreKeys.MENU);
        e.register(CoreKeys.WHEEL_MODIFIER);
        e.register(CoreKeys.LOADOUT_MODIFIER);
        e.register(CoreKeys.TRACKER);
    }

    private static void layers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(IsekaiMahou.id("core_hud"), new CoreHud());
        // Abaixo do chat: mensagens novas continuam por cima do rastreador
        e.registerBelow(VanillaGuiLayers.CHAT, IsekaiMahou.id("core_objectives"), new ObjectiveHud());
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(CoreRegistries.SPELL_PROJECTILE.get(), com.renanpontes.isekaimahou.client.spellfx.SpellProjectileRenderer::new);
    }

    private static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ClientMagic.clientTick();
        ObjectiveTracker.tick();
        while (CoreKeys.TRACKER.consumeClick()) {
            if (mc.screen == null) ObjectiveTracker.toggle();
        }

        if (ClientMagic.pendingPathScreen && mc.screen == null) {
            ClientMagic.pendingPathScreen = false;
            mc.setScreen(new PathSelectionScreen());
        }
        while (CoreKeys.MENU.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new StatusScreen());
        }
        for (int s = 0; s < CoreKeys.SLOTS.length; s++) {
            KeyMapping k = CoreKeys.SLOTS[s];
            while (k.consumeClick()) { /* esvazia a fila */ }
            boolean down = mc.screen == null && k.isDown();
            if (down != slotDown[s]) {
                slotDown[s] = down;
                PacketDistributor.sendToServer(new CorePayloads.CastKey(s, down));
            }
        }
    }

    private static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientMagic.reset();
        ObjectiveTracker.clear();
    }
}
