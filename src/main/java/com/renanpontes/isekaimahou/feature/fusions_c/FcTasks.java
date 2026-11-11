package com.renanpontes.isekaimahou.feature.fusions_c;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;

/**
 * Sequências curtas que rodam tick a tick no servidor (carga do Canhão Elétrico, detonações em cadeia da
 * Explosão de Vapor, onda do Zero Absoluto). Não são salvas: somem ao fechar o mundo.
 */
public final class FcTasks {
    /** Uma sequência: {@link #tick()} devolve false quando terminou. */
    public interface Task {
        boolean tick();
    }

    /** Teto de segurança: magias em massa não acumulam sequências sem fim. */
    private static final int MAX_TASKS = 256;
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    public static synchronized void add(Task t) {
        if (TASKS.size() + PENDING.size() < MAX_TASKS) PENDING.add(t);
    }

    /** Roda {@code r} daqui a {@code ticks} ticks. */
    public static void later(int ticks, Runnable r) {
        int[] left = {Math.max(0, ticks)};
        add(() -> {
            if (left[0]-- > 0) return true;
            r.run();
            return false;
        });
    }

    static void tick() {
        synchronized (FcTasks.class) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        TASKS.removeIf(t -> {
            try {
                return !t.tick();
            } catch (RuntimeException e) {
                IsekaiMahou.LOGGER.error("Erro numa sequência de magia (fusions_c)", e);
                return true;
            }
        });
    }

    static synchronized void clear() {
        TASKS.clear();
        PENDING.clear();
    }

    private FcTasks() {}
}
