package com.renanpontes.isekaimahou.feature.outfits.client;

import com.renanpontes.isekaimahou.feature.outfits.OutfitArmorItem;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do módulo "outfits": renderer 3D (GeckoLib) das roupas e partículas do traje completo. */
public class OutfitsClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        // o GeckoLib pede o renderer na primeira vez que a peça é desenhada (bem depois daqui)
        OutfitArmorItem.renderFactory = OutfitRenderProvider::new;
        NeoForge.EVENT_BUS.addListener(OutfitAmbience::onClientTick);
    }
}
