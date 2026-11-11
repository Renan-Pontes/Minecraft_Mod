package com.renanpontes.isekaimahou.feature.bosses_g.client;

import com.renanpontes.isekaimahou.feature.bosses_g.BgPayloads;
import com.renanpontes.isekaimahou.feature.bosses_g.BgRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do módulo "bosses_g": renderers GeckoLib, cabeças que somem ao olhar, espinhos e a névoa do esquecimento. */
public class BossesGClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(this::onRenderers);
        MemoryFog.init();
        BgPayloads.onMemoryWipe = MemoryFog::start;
    }

    private void onRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(BgRegistries.FALSE_HYDRA.get(), ctx -> new BgGeoRenderer<>(ctx, "false_hydra", false, 1.0F, 1.8F));
        e.registerEntityRenderer(BgRegistries.FALSE_HYDRA_HEAD.get(), HydraHeadRenderer::new);
        e.registerEntityRenderer(BgRegistries.MIMIC_KING.get(), ctx -> new BgGeoRenderer<>(ctx, "mimic_king", false, 1.0F, 1.3F));
        e.registerEntityRenderer(BgRegistries.VAULT_MIMIC.get(), ctx -> new BgGeoRenderer<>(ctx, "vault_mimic", false, 1.0F, 0.5F));
        e.registerEntityRenderer(BgRegistries.MANTICORE.get(), ctx -> new BgGeoRenderer<>(ctx, "manticore", true, 1.0F, 1.1F));
        e.registerEntityRenderer(BgRegistries.MANTICORE_SPIKE.get(), ManticoreSpikeRenderer::new);
    }
}
