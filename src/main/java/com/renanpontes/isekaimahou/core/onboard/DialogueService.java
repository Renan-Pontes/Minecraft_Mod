package com.renanpontes.isekaimahou.core.onboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Dialogue;
import com.renanpontes.isekaimahou.core.event.DialogueEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lado servidor de {@link Dialogue}: guarda que diálogos cada jogador tem abertos/na fila (para aceitar só o fim de
 * diálogos que o servidor mandou) e posta {@link DialogueEvent.Finished}.
 */
public final class DialogueService {
    private static final Map<UUID, Set<String>> OPEN = new HashMap<>();
    private static final int MAX_OPEN = 16;

    public static void show(ServerPlayer p, String id, Component speaker, int rgb, List<Dialogue.Page> pages) {
        if (p == null || p instanceof FakePlayer || id == null || id.isBlank() || pages == null) return;
        List<Dialogue.Page> clean = new ArrayList<>();
        for (Dialogue.Page page : pages) {
            if (page != null && page.text() != null) clean.add(page);
            if (clean.size() >= Dialogue.MAX_PAGES) break;
        }
        if (clean.isEmpty()) return;
        Set<String> open = OPEN.computeIfAbsent(p.getUUID(), u -> new LinkedHashSet<>());
        if (!open.contains(id) && open.size() >= MAX_OPEN) return; // fila absurda: algum módulo em laço
        open.add(id);
        PacketDistributor.sendToPlayer(p, new OnboardPayloads.DialogueShow(id,
                speaker == null ? Component.empty() : speaker, rgb & 0xFFFFFF, clean));
    }

    public static void close(ServerPlayer p, @Nullable String id) {
        if (p == null) return;
        Set<String> open = OPEN.get(p.getUUID());
        if (id == null) {
            OPEN.remove(p.getUUID());
        } else if (open != null) {
            open.remove(id);
            if (open.isEmpty()) OPEN.remove(p.getUUID());
        }
        PacketDistributor.sendToPlayer(p, new OnboardPayloads.DialogueClose(id == null ? "" : id));
    }

    public static boolean isOpen(ServerPlayer p, String id) {
        Set<String> open = OPEN.get(p.getUUID());
        return open != null && open.contains(id);
    }

    public static boolean isBusy(ServerPlayer p) {
        Set<String> open = OPEN.get(p.getUUID());
        return open != null && !open.isEmpty();
    }

    public static void signal(ServerPlayer p, String token) {
        if (p == null || token == null || token.isBlank() || !isBusy(p)) return;
        PacketDistributor.sendToPlayer(p, new OnboardPayloads.DialogueSignal(token));
    }

    /** O cliente avisou que terminou/pulou. Só vale para diálogos que este servidor abriu. */
    static void onDone(ServerPlayer p, String id, boolean skipped) {
        Set<String> open = OPEN.get(p.getUUID());
        if (open == null || !open.remove(id)) return;
        if (open.isEmpty()) OPEN.remove(p.getUUID());
        NeoForge.EVENT_BUS.post(new DialogueEvent.Finished(p, id, skipped));
    }

    /** Toda conjuração do jogador conta para páginas "cast". */
    static void onCast(SpellCastEvent.Post event) {
        if (event.getCaster() instanceof ServerPlayer sp && !(sp instanceof FakePlayer)) signal(sp, Dialogue.AWAIT_CAST);
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OPEN.remove(event.getEntity().getUUID());
    }

    static void clear() {
        OPEN.clear();
    }

    private DialogueService() {}
}
