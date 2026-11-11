package com.renanpontes.isekaimahou.feature.equipment;

import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.equipment.network.CastAnimPayload;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Módulo "equipment": acessórios do Curios (Focos Mágicos no espaço próprio "magic_focus", anéis, amuleto e
 * talismã nos espaços padrão) + animações de conjuração do jogador (Player Animator).
 * Dados do Curios (espaços, tags) e animações JSON são gerados por tools/gen/equipment.py.
 */
public class EquipmentModule implements Module {
    @Override
    public String id() {
        return "equipment";
    }

    @Override
    public void init(IEventBus modBus) {
        EquipmentItems.register(modBus);

        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("equipment-1");
            // Handler só roda no cliente; a classe de cliente é carregada apenas dentro do lambda
            r.playToClient(CastAnimPayload.TYPE, CastAnimPayload.STREAM_CODEC,
                    (p, c) -> com.renanpontes.isekaimahou.feature.equipment.client.CastAnimations.receive(p));
        });

        // Prioridade baixa e sem receber cancelados: quem cancela a magia (Lei "Anulação", áreas anti-magia)
        // decide antes, e o foco não solta faíscas numa conjuração que não vai acontecer
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, SpellCastEvent.Pre.class, FocusBonuses::onPre);
        NeoForge.EVENT_BUS.addListener(CastAnimSync::onPost);
        NeoForge.EVENT_BUS.addListener(CastAnimSync::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(CastAnimSync::onStartTracking);
        NeoForge.EVENT_BUS.addListener(CastAnimSync::onLogout);
        NeoForge.EVENT_BUS.addListener(CastAnimSync::onRespawn);
        NeoForge.EVENT_BUS.addListener(CastAnimSync::onServerStopped);
    }
}
