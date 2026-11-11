package com.renanpontes.isekaimahou.glyph;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.server.MinecraftServer;

/**
 * Agenda ações no servidor para daqui a N ticks (usado por Atraso, Repetir, zonas, Domínio...).
 * Rodado pelo tick do servidor em {@code CoreEvents}. Não é salvo: ações pendentes somem ao fechar o mundo.
 */
public final class SpellScheduler {
    private record Task(long runAt, Runnable action) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();
    private static long now;

    public static synchronized void schedule(int delayTicks, Runnable action) {
        PENDING.add(new Task(now + Math.max(1, delayTicks), action));
    }

    public static void tick(MinecraftServer server) {
        now++;
        synchronized (SpellScheduler.class) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        Iterator<Task> it = TASKS.iterator();
        List<Runnable> run = new ArrayList<>();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.runAt <= now) {
                run.add(t.action);
                it.remove();
            }
        }
        for (Runnable r : run) {
            try {
                r.run();
            } catch (Exception e) {
                com.renanpontes.isekaimahou.IsekaiMahou.LOGGER.error("Erro numa ação agendada de magia", e);
            }
        }
    }

    public static synchronized void clear() {
        TASKS.clear();
        PENDING.clear();
    }

    private SpellScheduler() {}
}
