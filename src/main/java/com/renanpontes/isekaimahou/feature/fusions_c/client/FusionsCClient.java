package com.renanpontes.isekaimahou.feature.fusions_c.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.fusions_c.FcNet;
import com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente das Misturas C: renderers das entidades de magia e o clarão de tela. */
public class FusionsCClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(FusionsCClient::renderers);
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, IsekaiMahou.id("fusions_c_flash"), FcFlash::render));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> FcFlash.tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> FcFlash.reset());
        FcNet.clientFlash = FcFlash::onFlash;
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(FcRegistries.LAVA_GEYSER.get(), LavaGeyserRenderer::new);
        e.registerEntityRenderer(FcRegistries.MINI_VOLCANO.get(), ctx -> new FcGeoRenderer<>(ctx, "mini_volcano", 1.6F));
        e.registerEntityRenderer(FcRegistries.LAVA_BOMB.get(), LavaBombRenderer::new);
        e.registerEntityRenderer(FcRegistries.FUSION_SHOCKWAVE.get(), FusionShockwaveRenderer::new);
        e.registerEntityRenderer(FcRegistries.FUSION_BEAM.get(), FusionBeamRenderer::new);
        e.registerEntityRenderer(FcRegistries.BALL_LIGHTNING.get(), BallLightningRenderer::new);
        e.registerEntityRenderer(FcRegistries.ICE_COFFIN.get(), IceCoffinRenderer::new);
        e.registerEntityRenderer(FcRegistries.WORLD_ROOT.get(), ctx -> new FcGeoRenderer<>(ctx, "world_root", 0.6F));
        e.registerEntityRenderer(FcRegistries.POLLEN_CLOUD.get(), PollenCloudRenderer::new);
        e.registerEntityRenderer(FcRegistries.SAND_COFFIN.get(), SandCoffinRenderer::new);
        e.registerEntityRenderer(FcRegistries.SAND_TSUNAMI.get(), SandTsunamiRenderer::new);
        e.registerEntityRenderer(FcRegistries.SUPERNOVA.get(), SupernovaRenderer::new);
        e.registerEntityRenderer(FcRegistries.SACRED_SPRING.get(), SacredSpringRenderer::new);
    }
}
