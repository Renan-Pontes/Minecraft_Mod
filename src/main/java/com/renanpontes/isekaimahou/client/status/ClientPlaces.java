package com.renanpontes.isekaimahou.client.status;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Places;

import net.minecraft.client.Minecraft;

/**
 * Lugar onde o jogador local está agora (cidade, capital...), sincronizado pelo servidor a cada entrada/saída.
 * <pre>
 * Places.Place aqui = ClientPlaces.current();   // null fora de qualquer lugar
 * </pre>
 */
public final class ClientPlaces {
    @Nullable private static Places.Place current;

    /** Lugar atual do jogador local, ou null. */
    @Nullable
    public static Places.Place current() {
        Minecraft mc = Minecraft.getInstance();
        if (current == null || mc.level == null || !mc.level.dimension().equals(current.dim())) return null;
        return current;
    }

    static void set(@Nullable Places.Place place) {
        current = place;
    }

    static void clear() {
        current = null;
    }

    private ClientPlaces() {}
}
