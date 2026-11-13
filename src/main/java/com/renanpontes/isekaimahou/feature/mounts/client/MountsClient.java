package com.renanpontes.isekaimahou.feature.mounts.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.mounts.MountsRegistries;
import com.renanpontes.isekaimahou.feature.mounts.entity.AuroraStag;
import com.renanpontes.isekaimahou.feature.mounts.entity.Griffin;
import com.renanpontes.isekaimahou.feature.mounts.entity.LandDrake;
import com.renanpontes.isekaimahou.feature.mounts.entity.Nightmare;
import com.renanpontes.isekaimahou.feature.mounts.entity.Pegasus;
import com.renanpontes.isekaimahou.feature.mounts.entity.SkyRay;
import com.renanpontes.isekaimahou.feature.mounts.entity.Wyvern;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do modulo "mounts": renderers GeckoLib, tecla de habilidade, controle de voo e HUD. */
public class MountsClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(MountsClient::renderers);
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(MountInput.ABILITY));
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAbove(VanillaGuiLayers.HOTBAR, IsekaiMahou.id("mounts_ability"), MountHud::render));
        NeoForge.EVENT_BUS.addListener(MountInput::onMovementInput);
        NeoForge.EVENT_BUS.addListener(MountInput::onClientTick);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(MountsRegistries.GRIFFIN.get(),
                ctx -> new MountRenderer<Griffin>(ctx, Griffin.ID, true, false, 1.0F, 0.9F));
        e.registerEntityRenderer(MountsRegistries.SKY_RAY.get(),
                ctx -> new MountRenderer<SkyRay>(ctx, SkyRay.ID, false, true, 1.1F, 0.3F));
        e.registerEntityRenderer(MountsRegistries.PEGASUS.get(),
                ctx -> new MountRenderer<Pegasus>(ctx, Pegasus.ID, true, true, 0.9F, 0.9F));
        e.registerEntityRenderer(MountsRegistries.WYVERN.get(),
                ctx -> new MountRenderer<Wyvern>(ctx, Wyvern.ID, true, true, 1.2F, 1.0F));
        e.registerEntityRenderer(MountsRegistries.LAND_DRAKE.get(),
                ctx -> new GroundMountRenderer<LandDrake>(ctx, LandDrake.ID, false, 0.7F));
        e.registerEntityRenderer(MountsRegistries.NIGHTMARE.get(),
                ctx -> new GroundMountRenderer<Nightmare>(ctx, Nightmare.ID, true, 0.75F));
        e.registerEntityRenderer(MountsRegistries.AURORA_STAG.get(),
                ctx -> new GroundMountRenderer<AuroraStag>(ctx, AuroraStag.ID, true, 0.7F));
    }
}
