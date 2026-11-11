package com.renanpontes.isekaimahou.feature.automation.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.automation.AutoRegistries;
import com.renanpontes.isekaimahou.feature.automation.block.ManaBatteryBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.block.ManaBatteryItem;
import com.renanpontes.isekaimahou.feature.automation.block.ManaCondenserBlockEntity;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Parte de cliente do módulo "automation": telas, desenho do Pedestal, carga da Bateria no item e dicas de combustível. */
public class AutomationClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> {
            e.register(AutoRegistries.RUNIC_PEDESTAL_MENU.get(), RunicPedestalScreen::new);
            e.register(AutoRegistries.MANA_CONDENSER_MENU.get(), ManaCondenserScreen::new);
        });
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(AutoRegistries.RUNIC_PEDESTAL_BE.get(), RunicPedestalRenderer::new));
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() ->
                ItemProperties.register(AutoRegistries.MANA_BATTERY_ITEM.get(), IsekaiMahou.id("charge"),
                        (stack, level, entity, seed) -> ManaBatteryItem.stored(stack) / (float) ManaBatteryBlockEntity.CAPACITY)));
        NeoForge.EVENT_BUS.addListener(AutomationClient::onTooltip);
    }

    /** Com a tela do Condensador aberta, os combustíveis mostram quanto mana rendem. */
    private static void onTooltip(ItemTooltipEvent e) {
        if (!(Minecraft.getInstance().screen instanceof ManaCondenserScreen)) return;
        int value = ManaCondenserBlockEntity.fuelValue(e.getItemStack());
        if (value > 0) {
            e.getToolTip().add(Component.translatable("gui.isekai_mahou.condenser.fuel_value", value).withColor(0xB896FF));
        }
    }
}
