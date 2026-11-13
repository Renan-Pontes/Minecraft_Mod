package com.renanpontes.isekaimahou.client.onboard;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.PathSelectionScreen;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.onboard.OnboardPayloads;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Entrada no jogo no cliente: sabe se o jogador ainda está "quieto" (escolhendo a classe / no tutorial) e guarda os
 * avisos de chat que não devem aparecer nessa hora (teclas de outros mods trocadas, Curios...). Os avisos saem quando
 * o servidor diz que o silêncio acabou ({@code OnboardingEvent.TutorialDone} ou 5 min depois da escolha) ou, se o
 * servidor nunca disser nada, 5 min depois de entrar no mundo.
 */
public final class ClientOnboarding {
    private static final int MAX_DEFERRED = 64;
    private static final List<Component> DEFERRED = new ArrayList<>();
    private static boolean known;
    private static boolean quiet = true;
    private static boolean tutorialDone;
    private static int ticksInWorld;

    /** O jogador ainda está na entrada (sem mensagens extras na tela). Antes da resposta do servidor: true. */
    public static boolean quiet() {
        return quiet;
    }

    /** Tutorial concluído (segundo o servidor). */
    public static boolean tutorialDone() {
        return tutorialDone;
    }

    /**
     * Mostra uma linha no chat agora, ou a guarda até o fim da entrada no jogo. Use para avisos de sistema que não
     * são urgentes (ex.: "a tecla X foi trocada").
     */
    public static void defer(Component line) {
        if (line == null) return;
        if (known && !quiet) {
            show(line);
            return;
        }
        if (DEFERRED.size() < MAX_DEFERRED) DEFERRED.add(line);
    }

    static void applyState(OnboardPayloads.OnboardState s) {
        known = true;
        quiet = s.quiet();
        tutorialDone = s.tutorialDone();
        if (s.hideTracker()) ObjectiveTracker.setVisible(false);
        if (!quiet) flush();
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ticksInWorld++;
        // Reserva: servidor sem resposta (não deveria acontecer) — não guarda os avisos para sempre
        if (!known && ticksInWorld >= Onboarding.QUIET_TICKS) {
            known = true;
            quiet = false;
        }
        if (!quiet && !DEFERRED.isEmpty()) flush();
    }

    private static void flush() {
        if (Minecraft.getInstance().player == null || DEFERRED.isEmpty()) return;
        List<Component> lines = new ArrayList<>(DEFERRED);
        DEFERRED.clear();
        for (Component line : lines) show(line);
    }

    private static void show(Component line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(line, false);
        else if (DEFERRED.size() < MAX_DEFERRED) DEFERRED.add(line);
    }

    /**
     * v1.0 — cutucão do servidor: a criação do personagem está pendente. Só age se o jogador estiver vivo e SEM tela
     * nenhuma (a tela de classe/raça aberta, a de morte, o chat ou o menu de pausa não são mexidos): algo fechou a tela
     * de criação, então o cliente pede ao servidor para reabrir (ele decide: classe ou etapa pendente — assim um
     * cutucão atrasado nunca reabre a tela de classe depois da escolha).
     */
    static void entryNudge() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (mc.screen instanceof PathSelectionScreen) {
            // pedido de abrir que chegou com a tela já aberta: sem isto ela reabriria logo depois da escolha
            ClientMagic.pendingPathScreen = false;
            return;
        }
        // (com um pedido de abrir já na fila a tela abre no próximo tick: não pede de novo)
        if (mc.screen != null || mc.player.isDeadOrDying() || ClientMagic.pendingPathScreen) return;
        PacketDistributor.sendToServer(OnboardPayloads.EntryResume.INSTANCE);
    }

    /** Saída do mundo: os avisos ainda guardados ficam para a próxima entrada (mesma sessão do jogo). */
    static void onLogout() {
        known = false;
        quiet = true;
        tutorialDone = false;
        ticksInWorld = 0;
    }

    private ClientOnboarding() {}
}
