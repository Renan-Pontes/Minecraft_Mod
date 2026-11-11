package com.renanpontes.isekaimahou.core.balance;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * v1.0 — inflação de números (§0.1). As constantes ficam em {@code core.Progression}; a vida/poder do JOGADOR é do
 * {@code CoreGameplay.updateGrowthHealth}; aqui ficam as criaturas ({@link MobScaling}). Chamado por
 * {@code ClassesCore.init}.
 */
public final class BalanceCore {
    private static boolean initialized;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        // Depois dos outros mods mexerem no spawn (ex.: chefes que trocam a vida base ao nascer)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, EntityJoinLevelEvent.class, MobScaling::onJoin);
        // Antes das reduções (armadura etc. acontecem depois deste evento de qualquer jeito)
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, LivingIncomingDamageEvent.class, MobScaling::onIncomingDamage);
    }

    private BalanceCore() {}
}
