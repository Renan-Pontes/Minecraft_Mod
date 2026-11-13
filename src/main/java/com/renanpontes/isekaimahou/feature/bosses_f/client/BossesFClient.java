package com.renanpontes.isekaimahou.feature.bosses_f.client;

import com.renanpontes.isekaimahou.feature.bosses_f.BfRegistry;
import com.renanpontes.isekaimahou.feature.bosses_f.entity.RuinColossus;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do módulo "bosses_f": renderers GeckoLib dos 4 chefes e dos projéteis. */
public class BossesFClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
        BfAtmosphere.init();
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(BfRegistry.SWAMP_HYDRA.get(), ctx -> new BfGeoRenderer<>(ctx, "swamp_hydra", false, 1.0F, 2.0F));
        e.registerEntityRenderer(BfRegistry.WYVERN_KING.get(), ctx -> new BfGeoRenderer<>(ctx, "wyvern_king", false, 1.25F, 1.4F));
        e.registerEntityRenderer(BfRegistry.DESERT_BASILISK.get(), ctx -> new BfGeoRenderer<>(ctx, "desert_basilisk", false, 1.0F, 1.6F));
        e.registerEntityRenderer(BfRegistry.RUIN_COLOSSUS.get(),
                ctx -> new BfGeoRenderer<>(ctx, "ruin_colossus", false, RuinColossus.SCALE, 2.4F));
        e.registerEntityRenderer(BfRegistry.VENOM_GLOB.get(), ctx -> new ThrownItemRenderer<>(ctx, 1.4F, true));
        e.registerEntityRenderer(BfRegistry.BOULDER.get(), BoulderRenderer::new);
    }
}
