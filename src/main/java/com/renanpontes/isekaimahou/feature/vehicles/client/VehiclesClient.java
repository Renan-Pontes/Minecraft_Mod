package com.renanpontes.isekaimahou.feature.vehicles.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.vehicles.VehicleKind;
import com.renanpontes.isekaimahou.feature.vehicles.VehiclesRegistries;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do módulo "vehicles": renderers GeckoLib, entradas do piloto e o medidor de energia. */
public class VehiclesClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            for (VehicleKind kind : VehicleKind.values()) {
                e.registerEntityRenderer(VehiclesRegistries.type(kind), ctx -> new VehicleRenderer(ctx, kind));
            }
        });
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAbove(VanillaGuiLayers.HOTBAR, IsekaiMahou.id("vehicles_energy"), VehicleHud::render));
        NeoForge.EVENT_BUS.addListener(VehicleInput::onMovementInput);
        NeoForge.EVENT_BUS.addListener(VehiclesClient::carpetMusic);
    }

    /** No Tapete Voador toca a música indiana (tema ambiente "flying_carpet"; chefes perto têm prioridade). */
    private static void carpetMusic(net.neoforged.neoforge.client.event.ClientTickEvent.Post e) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.isPaused() || mc.player.tickCount % 10 != 0) return;
        if (mc.player.getVehicle() instanceof com.renanpontes.isekaimahou.feature.vehicles.entity.MagicVehicle v
                && v.kind() == VehicleKind.CARPET) {
            com.renanpontes.isekaimahou.feature.boss_music.client.BossMusicApi.requestAmbient("flying_carpet");
        }
    }
}
