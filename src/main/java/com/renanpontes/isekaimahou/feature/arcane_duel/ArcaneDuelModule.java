package com.renanpontes.isekaimahou.feature.arcane_duel;

import com.renanpontes.isekaimahou.feature.arcane_duel.glyph.DuelGlyphs;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Módulo "arcane_duel" (v0.8): contramágica e mecânicas de Magic: The Gathering sobre a Pilha do núcleo.
 * Glifos: Vazamento de Mana, Redirecionar, Copiar Magia, Roubar Encanto, Toque Mortal, Atropelar, Instantâneo,
 * Resguardo, Indestrutível, Exilar, Ira Divina, Passo no Tempo e o gatilho Em Resposta.
 * Lendas: Contramágica Perfeita, Ira de Deus, Passo no Tempo. (A Contramágica em si é do módulo arcane.)
 */
public class ArcaneDuelModule implements Module {
    @Override
    public String id() {
        return "arcane_duel";
    }

    @Override
    public void init(IEventBus modBus) {
        DuelRegistry.register(modBus);
        DuelGlyphs.register();
        DuelLegends.register();
        modBus.addListener(DuelLegends::validate);

        NeoForge.EVENT_BUS.addListener(InResponse::onPending);
        NeoForge.EVENT_BUS.addListener(InResponse::onPre);
        NeoForge.EVENT_BUS.addListener(DuelEvents::onSpellDamagePre);
        NeoForge.EVENT_BUS.addListener(DuelEvents::onSpellDamagePost);
        NeoForge.EVENT_BUS.addListener(DuelEvents::onEffectApplicable);
        NeoForge.EVENT_BUS.addListener(DuelEvents::onCounterAttempt);
        NeoForge.EVENT_BUS.addListener(DuelEvents::onCountered);
        // Depois de outros ajustes de dano (armaduras, Infinito...): a égide corta o que sobrou
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DuelEvents::onLivingDamagePre);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, DuelEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(DuelEvents::onDrops);
        NeoForge.EVENT_BUS.addListener(DuelEvents::onXpDrop);
        NeoForge.EVENT_BUS.addListener(DuelEvents::onCastPost);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> DuelEvents.clear());
    }
}
