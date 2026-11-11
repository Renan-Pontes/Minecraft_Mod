package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tarefas atrasadas de poucos ticks (golpes extras do Zanjutsu com Dividir, o segundo estalo do Raio Branco...). Só em
 * memória, com teto de tarefas, e esvaziada ao parar o servidor. Cada tarefa confere sozinha se o conjurador ainda existe.
 */
public final class ShinigamiTasks {
    private static final int MAX_TASKS = 512;

    private static final class Task {
        int ticks;
        final Runnable run;

        Task(int ticks, Runnable run) {
            this.ticks = ticks;
            this.run = run;
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    /** Roda {@code run} daqui a {@code ticks} ticks do servidor (0 = no próximo). */
    public static void later(int ticks, Runnable run) {
        if (TASKS.size() + PENDING.size() >= MAX_TASKS) return;
        PENDING.add(new Task(Math.max(0, ticks), run));
    }

    static void tick() {
        if (!PENDING.isEmpty()) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        if (TASKS.isEmpty()) return;
        Iterator<Task> it = TASKS.iterator();
        List<Runnable> due = new ArrayList<>();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.ticks-- <= 0) {
                due.add(t.run);
                it.remove();
            }
        }
        for (Runnable r : due) {
            try {
                r.run();
            } catch (RuntimeException e) {
                com.renanpontes.isekaimahou.IsekaiMahou.LOGGER.warn("class_shinigami: tarefa atrasada falhou: {}", e.toString());
            }
        }
    }

    static void clear() {
        TASKS.clear();
        PENDING.clear();
    }

    private ShinigamiTasks() {}
}
