package com.renanpontes.isekaimahou.feature.class_shinigami.client;

import java.util.HashMap;
import java.util.Map;

import com.renanpontes.isekaimahou.feature.class_shinigami.ShinigamiNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Cliente: quem está com a máscara do Vazio (por id de entidade), recebida pelo pacote
 * {@link ShinigamiNetwork.HollowMask}. Cada entrada expira sozinha (o servidor reenvia a cada 2 s enquanto dura).
 */
public final class HollowClient {
    private record Mask(int kind, long until) {}

    private static final Map<Integer, Mask> MASKS = new HashMap<>();

    static void accept(ShinigamiNetwork.HollowMask p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (p.kind() <= 0 || p.ticks() <= 0) MASKS.remove(p.entityId());
        else MASKS.put(p.entityId(), new Mask(p.kind(), mc.level.getGameTime() + p.ticks() + 5));
    }

    /** 0 = sem máscara, 1 = Máscara do Vazio, 2 = Vasto Lorde. */
    public static int kind(Entity e) {
        Mask m = MASKS.get(e.getId());
        if (m == null) return 0;
        if (e.level().getGameTime() > m.until()) {
            MASKS.remove(e.getId());
            return 0;
        }
        return m.kind();
    }

    static void clear() {
        MASKS.clear();
    }

    /** Alguém está com máscara (para a aura de chamas)? */
    static boolean any() {
        return !MASKS.isEmpty();
    }

    /** Percorre (id da entidade, tipo) de quem está com máscara. */
    static void forEach(java.util.function.BiConsumer<Integer, Integer> action) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0L : mc.level.getGameTime();
        for (Map.Entry<Integer, Mask> e : MASKS.entrySet()) {
            if (now <= e.getValue().until()) action.accept(e.getKey(), e.getValue().kind());
        }
    }

    private HollowClient() {}
}
