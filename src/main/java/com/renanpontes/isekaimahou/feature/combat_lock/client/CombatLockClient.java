package com.renanpontes.isekaimahou.feature.combat_lock.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.module.ClientModule;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Parte de cliente do módulo "combat_lock": travar o alvo ({@link LockOn}, {@link LockMarker}) e o aviso "!!!" ({@link ThreatHud}). */
public class CombatLockClient implements ClientModule {
    /** Alvo travado pelo jogador local (ou null). Para outros módulos de cliente. */
    @Nullable
    public static LivingEntity lockedTarget() {
        return LockOn.target();
    }

    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(LockOn.LOCK_KEY);
            e.register(LockOn.NEXT_KEY);
        });
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAbove(VanillaGuiLayers.CROSSHAIR, IsekaiMahou.id("combat_lock_warn"), ThreatHud::render));

        IEventBus bus = NeoForge.EVENT_BUS;
        // Pre: a tecla é lida antes de o Minecraft tratar o "pegar bloco" do mesmo clique
        bus.addListener(ClientTickEvent.Pre.class, LockOn::onTick);
        bus.addListener(ClientTickEvent.Post.class, e -> {
            if (!Minecraft.getInstance().isPaused()) ThreatHud.tick();
        });
        bus.addListener(RenderFrameEvent.Pre.class, LockOn::onFrame);
        bus.addListener(ViewportEvent.ComputeCameraAngles.class, LockOn::onCamera);
        bus.addListener(RenderLevelStageEvent.class, LockMarker::render);
        bus.addListener(InputEvent.InteractionKeyMappingTriggered.class, LockOn::onInteraction);
        // Baixa prioridade: loadout, diálogo e rodas do mod usam a roda do mouse primeiro
        bus.addListener(EventPriority.LOW, false, InputEvent.MouseScrollingEvent.class, LockOn::onScroll);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            LockOn.reset();
            ThreatHud.clear();
        });
    }
}
