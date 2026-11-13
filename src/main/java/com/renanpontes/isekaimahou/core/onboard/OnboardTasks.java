package com.renanpontes.isekaimahou.core.onboard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Tarefas atrasadas do núcleo A2 (letreiro → evento, retomada do tutorial, avisos de viagem). Contam ticks do
 * servidor; ligadas a um jogador, só rodam se ele ainda estiver online. Esvaziadas ao parar o servidor.
 */
final class OnboardTasks {
    private record Task(long due, UUID player, String key, Runnable action) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static long now;

    /** Agenda {@code action(player)} daqui a {@code delay} ticks. {@code key} não vazia substitui a anterior igual. */
    static void later(ServerPlayer player, int delay, String key, java.util.function.Consumer<ServerPlayer> action) {
        UUID id = player.getUUID();
        if (!key.isEmpty()) cancel(id, key);
        MinecraftServer server = player.getServer();
        TASKS.add(new Task(now + Math.max(1, delay), id, key, () -> {
            if (server == null) return;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && !p.hasDisconnected()) action.accept(p);
        }));
    }

    /** Há uma tarefa com esta chave agendada para o jogador? */
    static boolean pending(UUID player, String key) {
        for (Task t : TASKS) if (t.player.equals(player) && t.key.equals(key)) return true;
        return false;
    }

    static void cancel(UUID player, String key) {
        TASKS.removeIf(t -> t.player.equals(player) && t.key.equals(key));
    }

    static void tick(ServerTickEvent.Post event) {
        now++;
        if (TASKS.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (t.due <= now) {
                due.add(t);
                it.remove();
            }
        }
        for (Task t : due) {
            try {
                t.action.run();
            } catch (RuntimeException e) {
                IsekaiMahou.LOGGER.error("[onboard] tarefa '{}' falhou", t.key, e);
            }
        }
    }

    static void clear() {
        TASKS.clear();
        now = 0;
    }

    private OnboardTasks() {}
}
