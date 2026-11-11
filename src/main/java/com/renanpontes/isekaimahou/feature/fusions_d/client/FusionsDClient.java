package com.renanpontes.isekaimahou.feature.fusions_d.client;

import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Parte de cliente do módulo "fusions_d": renderers de todas as manifestações e a camada do tentáculo do Kraken. */
public class FusionsDClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(FusionsDClient::renderers);
        modBus.addListener(FusionsDClient::layers);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(FdRegistries.RUBBLE.get(), RubbleRenderer::new);
        e.registerEntityRenderer(FdRegistries.BLACK_HOLE.get(), BlackHoleRenderer::new);
        e.registerEntityRenderer(FdRegistries.PLANET_CORE.get(), PlanetCoreRenderer::new);
        e.registerEntityRenderer(FdRegistries.CRYSTAL_LOTUS.get(), CrystalLotusRenderer::new);
        e.registerEntityRenderer(FdRegistries.CRYSTAL_SPIKE.get(), CrystalSpikeRenderer::new);
        e.registerEntityRenderer(FdRegistries.PRISM_BEAM.get(), PrismBeamRenderer::new);
        e.registerEntityRenderer(FdRegistries.STAR.get(), StarRenderer::new);
        e.registerEntityRenderer(FdRegistries.HELLFIRE_PILLAR.get(), HellfirePillarRenderer::new);
        e.registerEntityRenderer(FdRegistries.KRAKEN_TENTACLE.get(), KrakenTentacleRenderer::new);
        e.registerEntityRenderer(FdRegistries.ABYSSAL_MAW.get(), AbyssalMawRenderer::new);
        e.registerEntityRenderer(FdRegistries.GRAVITY_FIELD.get(), GravityFieldRenderer::new);
        e.registerEntityRenderer(FdRegistries.DIMENSION_CUT.get(), DimensionCutRenderer::new);
        e.registerEntityRenderer(FdRegistries.VOID_TEAR.get(), VoidTearRenderer::new);
        e.registerEntityRenderer(FdRegistries.TWILIGHT_PILLAR.get(), TwilightPillarRenderer::new);
        e.registerEntityRenderer(FdRegistries.YIN_YANG.get(), YinYangRenderer::new);
    }

    private static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(KrakenTentacleModel.LAYER, KrakenTentacleModel::createBodyLayer);
    }
}
