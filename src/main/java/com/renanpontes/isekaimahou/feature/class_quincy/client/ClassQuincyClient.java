package com.renanpontes.isekaimahou.feature.class_quincy.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.feature.class_quincy.QuincyRegistries;
import com.renanpontes.isekaimahou.feature.class_quincy.item.QuincyArmorItem;
import com.renanpontes.isekaimahou.feature.class_quincy.item.QuincyCrossItem;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Cliente do módulo Quincy: renderers (flecha espiritual emissiva, NPC GeckoLib, traje 3D), o Arco Espiritual que
 * aparece ao puxar a Cruz (propriedades pull/pulling), o arco de Reishi junto da mira e a aba "Quincy" do menu B.
 */
public class ClassQuincyClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        QuincyArmorItem.renderFactory = QuincyArmorRenderer.Provider::new;
        modBus.addListener(this::onRenderers);
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onGuiLayers);
        // efeitos de anime: arco se formando, asas/auréola, olhos, Heilig Bogen, Antítese...
        com.renanpontes.isekaimahou.feature.class_quincy.QuincyFxNet.clientForm = QuincyFxClient::onForm;
        com.renanpontes.isekaimahou.feature.class_quincy.QuincyFxNet.clientBurst = QuincyFxClient::onBurst;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(QuincyFxClient::tick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(QuincyFxClient::render);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> QuincyFxClient.clear());
        MenuTabs.register("quincy", 85, Component.translatable("tab.isekai_mahou.quincy"), QuincyTab::new);
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(QuincyRegistries.SPIRIT_ARROW.get(), SpiritArrowRenderer::new);
        e.registerEntityRenderer(QuincyRegistries.QUINCY_NPC.get(), QuincyNpcRenderer::new);
        e.registerEntityRenderer(QuincyRegistries.LIGHT_CAGE.get(), LightCageRenderer::new);
    }

    private void onClientSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> {
            for (Item cross : new Item[]{QuincyRegistries.CROSS.get(), QuincyRegistries.CROSS_T1.get(), QuincyRegistries.CROSS_T2.get(),
                    QuincyRegistries.CROSS_T3.get(), QuincyRegistries.CROSS_T4.get()}) {
                ItemProperties.register(cross, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) -> {
                    if (entity == null || entity.getUseItem() != stack) return 0F;
                    return (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F;
                });
                ItemProperties.register(cross, ResourceLocation.withDefaultNamespace("pulling"),
                        (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                                && stack.getItem() instanceof QuincyCrossItem ? 1F : 0F);
            }
        });
    }

    private void onGuiLayers(RegisterGuiLayersEvent e) {
        e.registerAbove(VanillaGuiLayers.CROSSHAIR, IsekaiMahou.id("quincy_reishi"), ReishiHud::render);
        e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, IsekaiMahou.id("quincy_fx_overlay"), QuincyFxClient::overlay);
    }
}
