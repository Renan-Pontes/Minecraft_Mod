package com.renanpontes.isekaimahou.feature.farm.build;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Fila global das construções mágicas. Orçamento fixo por tick ({@link #BUDGET} blocos no total), dividido entre as
 * obras em andamento — construir muito nunca trava o servidor.
 */
public final class BuildQueue {
    /** Blocos por tick somando todas as obras. */
    public static final int BUDGET = 320;
    /** Obras simultâneas por jogador. */
    public static final int MAX_PER_PLAYER = 4;

    private static final List<BuildJob> JOBS = new ArrayList<>();

    /** Enfileira; false se o jogador já tem obras demais andando. */
    public static boolean add(BuildJob job) {
        if (count(job.owner) >= MAX_PER_PLAYER) return false;
        JOBS.add(job);
        return true;
    }

    public static int count(UUID owner) {
        int n = 0;
        for (BuildJob j : JOBS) if (j.owner.equals(owner) && !j.finished()) n++;
        return n;
    }

    public static void onServerTick(ServerTickEvent.Post e) {
        if (JOBS.isEmpty()) return;
        MinecraftServer server = e.getServer();
        int budget = BUDGET;
        // Copia: uma obra pode terminar e outra ser criada (desfazer) durante o tick
        for (BuildJob job : new ArrayList<>(JOBS)) {
            if (budget <= 0) break;
            budget -= job.tick(server, budget);
        }
        JOBS.removeIf(BuildJob::finished);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        for (Iterator<BuildJob> it = JOBS.iterator(); it.hasNext(); ) {
            BuildJob j = it.next();
            if (j.owner.equals(id)) {
                j.cancel();
                it.remove();
            }
        }
        UndoHistory.forget(id);
    }

    public static void onServerStopped(ServerStoppedEvent e) {
        JOBS.forEach(BuildJob::cancel);
        JOBS.clear();
        UndoHistory.clear();
    }

    private BuildQueue() {}
}
