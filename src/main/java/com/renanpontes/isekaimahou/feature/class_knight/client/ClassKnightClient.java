package com.renanpontes.isekaimahou.feature.class_knight.client;

import com.renanpontes.isekaimahou.client.menu.MenuTabs;
import com.renanpontes.isekaimahou.feature.class_knight.KnightRegistries;
import com.renanpontes.isekaimahou.feature.class_knight.outfit.KnightArmorItem;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do "class_knight": armadura 3D (GeckoLib), espada espectral e a aba "Juramentos" do menu B. */
public class ClassKnightClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        KnightArmorItem.renderFactory = KnightRenderProvider::new;
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(KnightRegistries.SPECTRAL_SWORD.get(), SpectralSwordRenderer::new));
        InvisibleAirClient.init();
        KnightFxClient.init();
        MenuTabs.register("class_knight", 12, Component.translatable("screen.isekai_mahou.class_knight.tab"), OathTab::new);
    }
}
