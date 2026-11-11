package com.renanpontes.isekaimahou.feature.paths;

import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Módulo "paths": Caminhos (árvores de progressão por elemento), feitos-bônus, liberação de glifos e aba "Caminhos".
 *
 * v0.7: os nós Aprendiz..Arquimago vêm das Câmaras de Provação (TrialsApi.markCleared soma o contador
 * {@code trial.<el>.<rank>} → evento Counter → o nó é liberado no tick seguinte). Os glifos Arcanos chegam por
 * NÍVEL; ao escolher a classe o 1º elemento de afinidade é despertado com uma magia pronta.
 *
 * Fluxo: eventos de progresso do núcleo marcam o jogador como "pendente"; no tick seguinte
 * {@link PathProgress#check} libera o que foi cumprido. Um check periódico (a cada 5 s) cobre o que não gera evento.
 */
public class PathsModule implements Module {
    private static final int PERIODIC_CHECK = 100;

    @Override
    public String id() {
        return "paths";
    }

    @Override
    public void init(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, PathsNetwork::register);
        FeatTracker.register();
        PathsCommands.register();

        var bus = NeoForge.EVENT_BUS;
        bus.addListener(MagicProgressEvent.ElementXp.class, e -> PathProgress.markPending(e.getPlayer()));
        bus.addListener(MagicProgressEvent.Counter.class, e -> PathProgress.markPending(e.getPlayer()));
        bus.addListener(MagicProgressEvent.ElementAwakened.class, e -> PathProgress.markPending(e.getPlayer()));
        bus.addListener(MagicProgressEvent.LevelUp.class, PathsModule::onLevelUp);
        bus.addListener(MagicProgressEvent.CircleChanged.class, PathsModule::onCircleChanged);
        bus.addListener(MagicProgressEvent.PathChosen.class, PathsModule::onPathChosen);
        bus.addListener(PlayerTickEvent.Post.class, PathsModule::onPlayerTick);
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, PathsModule::onLogin);
        bus.addListener(ServerStoppedEvent.class, e -> PathProgress.clearPending());
    }

    /** Nível novo (inclui o banco de XP aplicado na Ascensão, que pode pular vários níveis): glifos Arcanos. */
    private static void onLevelUp(MagicProgressEvent.LevelUp event) {
        if (event.newLevel > event.oldLevel) PathProgress.teachArcane(event.getPlayer(), true);
    }

    /** Círculo novo: glifos Arcanos que esperavam o Círculo mínimo, leis novas para quem tem Domínio. */
    private static void onCircleChanged(MagicProgressEvent.CircleChanged event) {
        ServerPlayer player = event.getPlayer();
        if (event.newCircle > event.oldCircle) {
            PathProgress.teachArcane(player, true);
            PathProgress.teachLaws(player, true);
        }
        PathProgress.markPending(player);
    }

    /**
     * Classe escolhida (o núcleo já gravou as magias Arcanas iniciais): desperta o elemento de afinidade com a
     * magia pronta e começa o caminho Arcano.
     */
    private static void onPathChosen(MagicProgressEvent.PathChosen event) {
        ServerPlayer player = event.getPlayer();
        PathProgress.startAffinity(player, true);
        PathProgress.teachArcane(player, false);
        PathProgress.markPending(player);
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        // Morto (tela de respawn): espera; o pendente continua marcado para depois do respawn.
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
        boolean periodic = (player.tickCount + player.getId()) % PERIODIC_CHECK == 0;
        if (PathProgress.takePending(player) || periodic) PathProgress.check(player);
        if (periodic) {
            // Cobre classe/nível/Círculo mudados sem evento (ex.: /mahou path) e glifos registrados depois.
            PathProgress.startAffinity(player, false);
            PathProgress.teachArcane(player, true);
            PathProgress.teachLaws(player, true);
        }
    }

    /** No login: migra mundos antigos, completa glifos de nós antigos e verifica o que já foi cumprido. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PathProgress.ensureAll(player);
        PathProgress.markPending(player);
    }
}
