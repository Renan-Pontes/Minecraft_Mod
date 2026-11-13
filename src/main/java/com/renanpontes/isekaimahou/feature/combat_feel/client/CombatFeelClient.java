package com.renanpontes.isekaimahou.feature.combat_feel.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.anim.MahouAnimsClient;
import com.renanpontes.isekaimahou.feature.combat_feel.FeelPayloads;
import com.renanpontes.isekaimahou.module.ClientModule;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "combat_feel": a micro-pausa ({@link HitStop}) e o HUD colado na mira ({@link ComboHud}:
 * "Combo ×N" e o aviso de finalizar).
 */
public class CombatFeelClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAbove(VanillaGuiLayers.CROSSHAIR, IsekaiMahou.id("combat_feel_hud"), ComboHud::render));

        // O aparo do próprio jogador: o cliente já sabe na hora (sem esperar outro pacote)
        MahouAnimsClient.onLocalParry(() -> HitStop.trigger(FeelPayloads.STOP_PARRY));

        IEventBus bus = NeoForge.EVENT_BUS;
        // O fim da pausa é checado por QUADRO (tempo real): os ticks do cliente estão quase parados durante ela
        bus.addListener(RenderFrameEvent.Pre.class, e -> HitStop.update());
        bus.addListener(ClientTickEvent.Post.class, e -> {
            HitStop.update();
            if (!Minecraft.getInstance().isPaused()) ComboHud.tick();
        });
        bus.addListener(ViewportEvent.ComputeCameraAngles.class, HitStop::onCamera);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            HitStop.reset();
            ComboHud.clear();
        });
    }
}
