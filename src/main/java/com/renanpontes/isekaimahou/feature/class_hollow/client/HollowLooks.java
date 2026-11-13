package com.renanpontes.isekaimahou.feature.class_hollow.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.class_hollow.HollowNetwork;

import net.minecraft.world.entity.Entity;

/**
 * SÓ CLIENTE. A aparência de cada Hollow à vista (por id de entidade), recebida pelo pacote {@link HollowNetwork.Look}:
 * rank (máscara/ossos), arquétipo e se está liberado, com Hierro ou faminto. Limpa ao sair do mundo.
 */
public final class HollowLooks {
    public record Look(int rank, int release, int flags) {
        public boolean released() {
            return (flags & HollowNetwork.Look.RELEASED) != 0;
        }

        public boolean hierro() {
            return (flags & HollowNetwork.Look.HIERRO) != 0;
        }

        public boolean starving() {
            return (flags & HollowNetwork.Look.STARVING) != 0;
        }
    }

    private static final Map<Integer, Look> LOOKS = new HashMap<>();

    static void accept(HollowNetwork.Look p) {
        if (p.rank() < 0) LOOKS.remove(p.entity());
        else LOOKS.put(p.entity(), new Look(p.rank(), p.release(), p.flags()));
    }

    @Nullable
    public static Look of(Entity e) {
        return LOOKS.get(e.getId());
    }

    static boolean any() {
        return !LOOKS.isEmpty();
    }

    static void forEach(BiConsumer<Integer, Look> action) {
        LOOKS.forEach(action);
    }

    static void clear() {
        LOOKS.clear();
    }

    private HollowLooks() {}
}
