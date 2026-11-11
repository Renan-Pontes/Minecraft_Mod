package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Contadores do Cavaleiro Rúnico (em {@code MagicData.counters}, salvos e sincronizados pelo núcleo).
 * Os golpes rúnicos acontecem a cada ataque: em vez de mexer nos dados (e forçar uma sincronização inteira) a cada
 * golpe, eles se acumulam aqui e são gravados de uma vez a cada 10 s, ao sair e antes de qualquer conferência.
 */
public final class KnightCounters {
    /** Rank do Juramento (0..5). */
    public static final String OATH = "knight.oath";
    /** Golpes que soltaram uma runa gravada. */
    public static final String RUNE_PROC = "knight.rune_proc";
    /** Abates com espada na mão (golpe corpo a corpo). */
    public static final String SWORD_KILL = "knight.sword_kill";
    /** Chefes derrotados por perto (qualquer {@code boss.<mob>} do núcleo). */
    public static final String BOSSES = "knight.bosses";
    /** Espadas lendárias despertadas. */
    public static final String LEGEND_SWORDS = "knight.legend_swords";
    /** Flag de espada despertada: {@code knight.sword.<id da espada>}. */
    public static final String SWORD_FLAG = "knight.sword.";

    private static final Map<UUID, Integer> PENDING_PROCS = new HashMap<>();

    /** +1 golpe rúnico (gravado depois). */
    public static void addProc(ServerPlayer player) {
        PENDING_PROCS.merge(player.getUUID(), 1, Integer::sum);
    }

    /** Grava os golpes pendentes deste jogador. */
    public static void flush(ServerPlayer player) {
        Integer n = PENDING_PROCS.remove(player.getUUID());
        if (n != null && n > 0) MagicApi.addCounter(player, RUNE_PROC, n);
    }

    /** A cada 10 s: grava os pendentes de quem está online. */
    public static void flushAll(MinecraftServer server) {
        if (PENDING_PROCS.isEmpty()) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) flush(p);
        PENDING_PROCS.clear();
    }

    public static void forget(UUID id) {
        PENDING_PROCS.remove(id);
    }

    public static void clear() {
        PENDING_PROCS.clear();
    }

    private KnightCounters() {}
}
