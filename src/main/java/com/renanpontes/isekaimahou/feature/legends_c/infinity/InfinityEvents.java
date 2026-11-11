package com.renanpontes.isekaimahou.feature.legends_c.infinity;

import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Liga o Infinito aos eventos do jogo (no init do módulo). */
public final class InfinityEvents {
    public static void register() {
        // LOW: decide depois dos aumentos que mexem no dano (Toque Mortal etc.) — o Infinito tem a palavra final
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, SpellDamageEvent.Pre.class, Infinity::onSpellDamagePre);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, SpellDamageEvent.Post.class, Infinity::onSpellDamagePost);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, LivingIncomingDamageEvent.class, Infinity::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerTickEvent.Post.class, Infinity::onServerTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> Infinity.clear());
    }

    private InfinityEvents() {}
}
