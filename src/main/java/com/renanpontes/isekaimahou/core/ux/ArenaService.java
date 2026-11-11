package com.renanpontes.isekaimahou.core.ux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.renanpontes.isekaimahou.core.api.ArenaBounds;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Guarda as arenas de {@link ArenaBounds} (servidor, só em memória) e mantém os clientes em dia: a cada mudança, os
 * jogadores da dimensão recebem a lista inteira da dimensão; ao entrar no mundo, trocar de dimensão ou renascer, o
 * jogador recebe a lista da dimensão onde está.
 */
public final class ArenaService {
    /** id → arena (ordem de registro). Acesso só na thread do servidor; sincronizado por segurança contra módulos apressados. */
    private static final Map<String, ArenaBounds.Arena> ARENAS = new LinkedHashMap<>();

    public static ArenaBounds.Arena register(MinecraftServer server, ArenaBounds.Arena arena) {
        ArenaBounds.Arena old;
        synchronized (ARENAS) {
            old = ARENAS.put(arena.id(), arena);
        }
        if (server != null) {
            sync(server, arena.dimension());
            if (old != null && !old.dimension().equals(arena.dimension())) sync(server, old.dimension());
        }
        return arena;
    }

    public static void unregister(MinecraftServer server, String id) {
        if (id == null) return;
        ArenaBounds.Arena old;
        synchronized (ARENAS) {
            old = ARENAS.remove(id);
        }
        if (old != null && server != null) sync(server, old.dimension());
    }

    public static Optional<ArenaBounds.Arena> get(String id) {
        synchronized (ARENAS) {
            return Optional.ofNullable(id == null ? null : ARENAS.get(id));
        }
    }

    public static List<ArenaBounds.Arena> in(ResourceKey<Level> dim) {
        List<ArenaBounds.Arena> out = new ArrayList<>();
        synchronized (ARENAS) {
            for (ArenaBounds.Arena a : ARENAS.values()) if (a.dimension().equals(dim)) out.add(a);
        }
        return out;
    }

    /** Manda a lista da dimensão para todos os jogadores que estão nela. */
    private static void sync(MinecraftServer server, ResourceKey<Level> dim) {
        UxPayloads.ArenaSync payload = new UxPayloads.ArenaSync(dim, in(dim));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension().equals(dim) && !(p instanceof FakePlayer)) PacketDistributor.sendToPlayer(p, payload);
        }
    }

    private static void syncTo(ServerPlayer p) {
        if (p instanceof FakePlayer) return;
        ResourceKey<Level> dim = p.level().dimension();
        PacketDistributor.sendToPlayer(p, new UxPayloads.ArenaSync(dim, in(dim)));
    }

    static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) syncTo(sp);
    }

    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) syncTo(sp);
    }

    static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) syncTo(sp);
    }

    static void clear() {
        synchronized (ARENAS) {
            ARENAS.clear();
        }
    }

    private ArenaService() {}
}
