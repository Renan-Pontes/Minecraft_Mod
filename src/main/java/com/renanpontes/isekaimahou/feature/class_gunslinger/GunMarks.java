package com.renanpontes.isekaimahou.feature.class_gunslinger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;

/**
 * Alvos marcados pelo glifo Marcar Alvo: as balas de QUEM marcou acertam o alvo como tiro certeiro (×1,35, somado ao
 * tiro na cabeça). Estado só no servidor, curto (segundos) e limpo a cada 5 s e ao parar o servidor.
 */
public final class GunMarks {
    private record Mark(UUID owner, long until) {}

    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    /** Teto de marcas simultâneas (proteção contra laço de magias). */
    private static final int MAX = 512;

    public static void mark(Entity target, @Nullable Entity owner, long now, int ticks) {
        if (owner == null) return;
        if (MARKS.size() >= MAX && !MARKS.containsKey(target.getUUID())) return;
        MARKS.put(target.getUUID(), new Mark(owner.getUUID(), now + ticks));
    }

    /** O alvo está marcado por {@code owner}? */
    public static boolean marked(Entity target, @Nullable Entity owner, long now) {
        if (owner == null) return false;
        Mark m = MARKS.get(target.getUUID());
        return m != null && m.until >= now && m.owner.equals(owner.getUUID());
    }

    /** Remove as marcas vencidas (chamado a cada 100 ticks). */
    public static void prune(long now) {
        for (Iterator<Mark> it = MARKS.values().iterator(); it.hasNext(); ) {
            if (it.next().until < now) it.remove();
        }
    }

    public static void forget(UUID entity) {
        MARKS.remove(entity);
    }

    public static void clear() {
        MARKS.clear();
    }

    private GunMarks() {}
}
