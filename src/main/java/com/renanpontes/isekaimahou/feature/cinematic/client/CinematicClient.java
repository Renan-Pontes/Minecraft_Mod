package com.renanpontes.isekaimahou.feature.cinematic.client;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.module.ClientModule;
import com.renanpontes.isekaimahou.network.ClientHooks;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Parte de cliente do módulo "cinematic": anúncios (tela + balão no mundo), cântico palavra por palavra,
 * cutscenes (faixas, câmera orbitando, título, tremor) e a API {@link CameraShake}.
 * Substitui os ganchos do núcleo {@code ClientHooks.announce / chant / cutscene}.
 */
public class CinematicClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> e.register(CinematicKeys.SKIP));
        modBus.addListener(RegisterGuiLayersEvent.class, CinematicClient::layers);

        ClientHooks.announce = AnnounceOverlay::onAnnounce;
        ClientHooks.chant = WorldLabels::onChant;
        ClientHooks.cutscene = CutsceneDirector::play;

        var bus = NeoForge.EVENT_BUS;
        // Prioridade alta: a cena trava as teclas ANTES do vanilla e do núcleo (Z X C G, menu) lerem
        bus.addListener(EventPriority.HIGH, ClientTickEvent.Pre.class, e -> tick());
        bus.addListener(RenderFrameEvent.Pre.class, CutsceneDirector::onFrameStart);
        bus.addListener(EventPriority.LOW, ViewportEvent.ComputeCameraAngles.class, CutsceneDirector::onCameraAngles);
        bus.addListener(EventPriority.LOW, ViewportEvent.ComputeFov.class, CutsceneDirector::onComputeFov);
        bus.addListener(RenderHandEvent.class, CutsceneDirector::onRenderHand);
        bus.addListener(RenderHighlightEvent.Block.class, CutsceneDirector::onBlockHighlight);
        bus.addListener(EventPriority.HIGH, RenderGuiLayerEvent.Pre.class, CutsceneDirector::onGuiLayer);
        bus.addListener(EventPriority.LOWEST, MovementInputUpdateEvent.class, CutsceneDirector::onMovementInput);
        bus.addListener(EventPriority.HIGHEST, InputEvent.InteractionKeyMappingTriggered.class, CutsceneDirector::onInteraction);
        bus.addListener(EventPriority.HIGHEST, InputEvent.MouseScrollingEvent.class, CutsceneDirector::onScroll);
        bus.addListener(EventPriority.LOWEST, CalculatePlayerTurnEvent.class, CutsceneDirector::onPlayerTurn);
        bus.addListener(RenderLevelStageEvent.class, WorldLabels::render);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> reset());
        bus.addListener(ClientPlayerNetworkEvent.Clone.class, e -> CutsceneDirector.stop());
    }

    private static void layers(RegisterGuiLayersEvent e) {
        // Faixas por BAIXO do HUD (espectadores continuam vendo a hotbar); anúncios e título por cima de tudo
        e.registerBelowAll(IsekaiMahou.id("cinematic_bars"), CutsceneOverlay::renderBars);
        e.registerAboveAll(IsekaiMahou.id("cinematic_announce"), AnnounceOverlay::render);
        e.registerAboveAll(IsekaiMahou.id("cinematic_overlay"), CutsceneOverlay::renderOverlay);
    }

    private static void tick() {
        CutsceneDirector.tick();
        CameraShake.tick();
        AnnounceOverlay.tick();
        WorldLabels.tick();
    }

    /** Saiu do servidor/mundo: nada de câmera, fila ou balões pendurados. */
    private static void reset() {
        CutsceneDirector.stop();
        CameraShake.clear();
        AnnounceOverlay.clear();
        WorldLabels.clear();
    }
}
