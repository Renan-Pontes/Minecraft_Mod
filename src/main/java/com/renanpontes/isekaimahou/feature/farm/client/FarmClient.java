package com.renanpontes.isekaimahou.feature.farm.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.farm.FarmRegistries;
import com.renanpontes.isekaimahou.feature.farm.machine.ElementalPedestalItem;
import com.renanpontes.isekaimahou.feature.farm.machine.MachineKind;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do módulo "farm": tela das fazendas, desenhos (máquinas e canteiro), prévias e o mini-HUD da varinha. */
public class FarmClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(FarmRegistries.MACHINE_MENU.get(), FarmMachineScreen::new));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(FarmRegistries.MACHINE_BE.get(), FarmMachineRenderer::new);
            e.registerBlockEntityRenderer(FarmRegistries.SITE_BE.get(), ConstructionSiteRenderer::new);
        });
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAbove(VanillaGuiLayers.CROSSHAIR, IsekaiMahou.id("farm_wand_hud"), FarmOverlay::renderHud));
        // O item do Pedestal Elemental troca de modelo pelo elemento guardado no estado do bloco
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() ->
                ItemProperties.register(FarmRegistries.MACHINE_ITEMS.get(MachineKind.ESSENCE).get(), IsekaiMahou.id("element"),
                        (stack, level, entity, seed) -> Element.BASE.indexOf(ElementalPedestalItem.element(stack)) / 10F)));
        NeoForge.EVENT_BUS.addListener(FarmOverlay::onRender);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> FarmMachineRenderer.clearCache());
    }
}
