package com.renanpontes.isekaimahou.feature.dimensions.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.dimensions.DimClientBridge;
import com.renanpontes.isekaimahou.feature.dimensions.DimRegistry;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

/**
 * Parte de cliente do modulo "dimensions": céus mágicos (Plano Elemental, Santuário, Fenda), o feixe do
 * Farol Celestial, o cubo estrelado da Fenda e a tela do Altar da Convergência.
 * (O Abismo usa os efeitos do Nether: névoa densa e sem céu.)
 */
public class DimensionsClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        DimClientBridge.openAltar = pos -> Minecraft.getInstance().setScreen(new ConvergenceScreen(pos));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(DimRegistry.CELESTIAL_BEACON_BE.get(), CelestialBeaconRenderer::new);
            e.registerBlockEntityRenderer(DimRegistry.TWILIGHT_RIFT_BE.get(), TwilightRiftRenderer::new);
        });
        modBus.addListener((RegisterDimensionSpecialEffectsEvent e) -> {
            e.register(IsekaiMahou.id("elemental_plane"), new MagicSkyEffects(MagicSkyEffects.Style.ELEMENTAL));
            e.register(IsekaiMahou.id("celestial_sanctuary"), new MagicSkyEffects(MagicSkyEffects.Style.CELESTIAL));
            e.register(IsekaiMahou.id("twilight_rift"), new MagicSkyEffects(MagicSkyEffects.Style.TWILIGHT));
        });
    }
}
