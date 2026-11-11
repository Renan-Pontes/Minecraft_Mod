package com.renanpontes.isekaimahou.feature.dimensions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Agendador mínimo de tarefas do servidor (sequências de efeitos e cutscenes atrasadas).
 * Cada tarefa recebe o tick atual (0..duração-1). Tudo roda na thread do servidor.
 */
public final class DimTasks {
    private static final class Task {
        final int duration;
        final IntConsumer step;
        int age;

        Task(int duration, IntConsumer step) {
            this.duration = duration;
            this.step = step;
        }
    }

    private static final List<Task> ACTIVE = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    /** Roda {@code step} uma vez por tick durante {@code duration} ticks. */
    public static void run(int duration, IntConsumer step) {
        PENDING.add(new Task(duration, step));
    }

    /** Roda {@code action} depois de {@code delay} ticks. */
    public static void later(int delay, Runnable action) {
        run(delay + 1, t -> {
            if (t == delay) action.run();
        });
    }

    static void tick() {
        ACTIVE.addAll(PENDING);
        PENDING.clear();
        Iterator<Task> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            try {
                t.step.accept(t.age);
            } catch (RuntimeException e) {
                com.renanpontes.isekaimahou.IsekaiMahou.LOGGER.error("Tarefa do módulo de dimensões falhou", e);
                it.remove();
                continue;
            }
            if (++t.age >= t.duration) it.remove();
        }
    }

    static void clear() {
        ACTIVE.clear();
        PENDING.clear();
    }

    private DimTasks() {}
}
