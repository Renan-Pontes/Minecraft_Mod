package com.renanpontes.isekaimahou.feature.class_gunslinger.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsNetwork;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsRegistries;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunKind;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.GunslingerArmorItem;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Cliente do Pistoleiro: renderers (bala traçante, Forasteiro GeckoLib, traje GeckoLib), pose/coice das armas,
 * segunda pistola das Pistolas Duplas, luneta do fuzil, tambor ao lado da mira, animações (playerAnimator) e a aba
 * "Caminho do Gatilho" do menu B.
 */
public class ClassGunslingerClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        GunslingerArmorItem.renderFactory = GsArmorRenderer.Provider::new;
        GsNetwork.clientAnim = GunAnimations::receive;
        GunAnimations.init();
        GsFxClient.init();

        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, e -> {
            e.registerEntityRenderer(GsRegistries.MANA_BULLET.get(), BulletRenderer::new);
            e.registerEntityRenderer(GsRegistries.OUTLAW.get(), OutlawRenderer::new);
            e.registerEntityRenderer(GsRegistries.PHOTON_SLASH.get(), PhotonSlashRenderer::new);
        });
        modBus.addListener(EntityRenderersEvent.AddLayers.class, GunRender::addLayers);
        modBus.addListener(RegisterClientExtensionsEvent.class, e -> {
            Item[] guns = new Item[GunKind.values().length + GsRegistries.REVOLVER_TIERS.size()];
            int i = 0;
            for (GunKind k : GunKind.values()) guns[i++] = GsRegistries.GUNS.get(k).get();
            for (var t : GsRegistries.REVOLVER_TIERS) guns[i++] = t.get();
            e.registerItem(new GunRender.Extensions(), guns);
        });
        modBus.addListener(RegisterGuiLayersEvent.class,
                e -> e.registerAbove(VanillaGuiLayers.CROSSHAIR, IsekaiMahou.id("gunslinger_drum"), new DrumHud()));

        NeoForge.EVENT_BUS.addListener(RenderHandEvent.class, GunRender::onRenderHand);
        NeoForge.EVENT_BUS.addListener(ComputeFovModifierEvent.class, GunRender::onFov);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, GunAnimations::onClientTick);

        MenuTabs.register("gunslinger_path", 85, Component.translatable("tab.isekai_mahou.gunslinger_path"), TriggerPathTab::new);
    }
}
