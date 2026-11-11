package com.renanpontes.isekaimahou.feature.arcane;

import com.renanpontes.isekaimahou.feature.arcane.glyph.ArcaneGlyphs;
import com.renanpontes.isekaimahou.feature.arcane.network.OreRevealPayload;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Módulo "arcane": efeitos neutros (Arcano, usáveis em magia de qualquer elemento) e gatilhos
 * condicionais/sequência (Ao Receber Dano, Vida Baixa, Ao Matar, Ao Acertar Crítico, Depois, Junto).
 */
public class ArcaneModule implements Module {
    @Override
    public String id() {
        return "arcane";
    }

    @Override
    public void init(IEventBus modBus) {
        ArcaneRegistries.register(modBus);
        ArcaneGlyphs.register();
        ArcaneTriggers.registerGlyphs();

        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("arcane-1");
            // O handler só roda no cliente; a classe de cliente é carregada apenas dentro do lambda
            r.playToClient(OreRevealPayload.TYPE, OreRevealPayload.STREAM_CODEC,
                    (p, c) -> com.renanpontes.isekaimahou.feature.arcane.client.OreRevealRenderer.receive(p));
        });

        NeoForge.EVENT_BUS.addListener(ArcaneTriggers::onPre);
        NeoForge.EVENT_BUS.addListener(ArcaneTriggers::onPost);
        NeoForge.EVENT_BUS.addListener(ArcaneTriggers::onDamage);
        NeoForge.EVENT_BUS.addListener(ArcaneTriggers::onDeath);
        NeoForge.EVENT_BUS.addListener(ArcaneTriggers::onCrit);
        NeoForge.EVENT_BUS.addListener(ArcaneEvents::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(ArcaneEvents::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ArcaneEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(ArcaneEvents::onServerStopped);
        NeoForge.EVENT_BUS.addListener(RecallMarks::onClone);
    }
}
