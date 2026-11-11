package com.renanpontes.isekaimahou.feature.class_quincy;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.entity.LivingEntity;

/**
 * Registro curto das FERIDAS recentes (dano real sofrido nos últimos {@link #WINDOW} ticks) de cada criatura — o que a
 * Antítese troca entre dois alvos. Só em memória, com teto por criatura e no total; limpo de tempos em tempos.
 */
public final class WoundLedger {
    /** Janela da Antítese: 10 s. */
    public static final int WINDOW = 200;
    private static final int MAX_PER_ENTITY = 32;
    private static final int MAX_ENTITIES = 2048;

    private record Wound(long time, float amount) {}

    private static final Map<UUID, ArrayDeque<Wound>> WOUNDS = new HashMap<>();

    /** Anota um dano real sofrido. */
    static void record(LivingEntity e, float amount) {
        if (amount <= 0 || e.level().isClientSide) return;
        if (WOUNDS.size() >= MAX_ENTITIES && !WOUNDS.containsKey(e.getUUID())) return;
        ArrayDeque<Wound> q = WOUNDS.computeIfAbsent(e.getUUID(), k -> new ArrayDeque<>());
        q.addLast(new Wound(e.level().getGameTime(), amount));
        while (q.size() > MAX_PER_ENTITY) q.removeFirst();
    }

    /** Soma do dano sofrido nos últimos {@link #WINDOW} ticks. */
    public static float recent(LivingEntity e) {
        ArrayDeque<Wound> q = WOUNDS.get(e.getUUID());
        if (q == null) return 0F;
        long now = e.level().getGameTime();
        float sum = 0F;
        for (Wound w : q) if (now - w.time() <= WINDOW) sum += w.amount();
        return sum;
    }

    /** As feridas foram trocadas: zera o registro de quem participou. */
    public static void clear(LivingEntity e) {
        WOUNDS.remove(e.getUUID());
    }

    /** Joga fora o que já passou da janela. */
    static void purge(long now) {
        Iterator<Map.Entry<UUID, ArrayDeque<Wound>>> it = WOUNDS.entrySet().iterator();
        while (it.hasNext()) {
            ArrayDeque<Wound> q = it.next().getValue();
            while (!q.isEmpty() && now - q.peekFirst().time() > WINDOW) q.removeFirst();
            if (q.isEmpty()) it.remove();
        }
    }

    static void forget(UUID id) {
        WOUNDS.remove(id);
    }

    static void clearAll() {
        WOUNDS.clear();
    }

    private WoundLedger() {}
}
