package com.renanpontes.isekaimahou.feature.fusions_b;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequências curtas que rodam tick a tick no servidor (Constelação, Portal Infernal, Julgamento Final).
 * Não são salvas: somem ao fechar o mundo, como as ações agendadas do núcleo.
 */
public final class FbTasks {
    /** Uma sequência: {@link #tick()} devolve false quando terminou. */
    public interface Task {
        boolean tick();
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    public static synchronized void add(Task t) {
        PENDING.add(t);
    }

    static void tick() {
        synchronized (FbTasks.class) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        TASKS.removeIf(t -> {
            try {
                return !t.tick();
            } catch (RuntimeException e) {
                com.renanpontes.isekaimahou.IsekaiMahou.LOGGER.error("Erro numa sequência de magia (fusions_b)", e);
                return true;
            }
        });
    }

    static synchronized void clear() {
        TASKS.clear();
        PENDING.clear();
    }

    private FbTasks() {}
}
