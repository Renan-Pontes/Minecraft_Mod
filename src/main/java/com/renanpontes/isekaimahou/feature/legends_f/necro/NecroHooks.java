package com.renanpontes.isekaimahou.feature.legends_f.necro;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Pontos de entrada do Exército das Sombras para o módulo (eventos e tick do servidor). */
public final class NecroHooks {
    /** Prioridade mais baixa: mortes canceladas (Totem, Coração do Demônio) não deixam sombra. */
    public static void onDeath(LivingDeathEvent e) {
        ShadowRemnants.onDeath(e);
    }

    public static void tick(MinecraftServer server) {
        ShadowRemnants.tick(server);
        ShadowArmy.tick(server);
        MonarchState.tick(server);
    }

    public static void clear() {
        ShadowRemnants.clear();
        ShadowArmy.clear();
        MonarchState.clear();
    }

    private NecroHooks() {}
}
