package com.renanpontes.isekaimahou.feature.earth_wind;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntPredicate;

import com.renanpontes.isekaimahou.IsekaiMahou;

/**
 * Tarefas curtas que rodam a cada tick do servidor (animacoes de construcao, vortices, ondas de tremor...).
 * Nao sao salvas: tudo aqui dura poucos segundos e o que precisa persistir (blocos temporarios) vai para {@link TempBlocks}.
 */
public final class EwTasks {
    private static final List<Running> TASKS = new ArrayList<>();
    private static final List<Running> PENDING = new ArrayList<>();

    private static final class Running {
        final IntPredicate step;
        final int maxTicks;
        int delay;
        int age;

        Running(IntPredicate step, int maxTicks, int delay) {
            this.step = step;
            this.maxTicks = maxTicks;
            this.delay = delay;
        }
    }

    /**
     * Roda {@code step.test(idade)} uma vez por tick (idade 0, 1, 2...) por ate {@code ticks} ticks.
     * A tarefa para antes se {@code step} retornar false. O primeiro passo roda no proximo tick.
     */
    public static void repeat(int ticks, IntPredicate step) {
        PENDING.add(new Running(step, ticks, 0));
    }

    /** Roda {@code action} uma vez daqui a {@code delay} ticks. */
    public static void later(int delay, Runnable action) {
        PENDING.add(new Running(age -> {
            action.run();
            return false;
        }, 1, Math.max(0, delay - 1)));
    }

    static void tick() {
        if (!PENDING.isEmpty()) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        Iterator<Running> it = TASKS.iterator();
        while (it.hasNext()) {
            Running r = it.next();
            if (r.delay > 0) {
                r.delay--;
                continue;
            }
            boolean keep;
            try {
                keep = r.step.test(r.age);
            } catch (RuntimeException e) {
                IsekaiMahou.LOGGER.error("Erro numa tarefa do modulo earth_wind", e);
                keep = false;
            }
            r.age++;
            if (!keep || r.age >= r.maxTicks) it.remove();
        }
    }

    static void clear() {
        TASKS.clear();
        PENDING.clear();
    }

    private EwTasks() {}
}
