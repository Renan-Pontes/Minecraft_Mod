package com.renanpontes.isekaimahou.feature.domain;

import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Módulo "domain": ultimates e Domínio (estilo Jujutsu Kaisen).
 * <ul>
 *   <li>Formas: Cataclismo (ultimate de área), Expansão de Domínio (ultimate, aceita Leis), Domínio Simples (defesa);</li>
 *   <li>Leis: Acerto Garantido, Sem Fuga, Território, Anulação, Tempo Lento, Gravidade do Domínio,
 *       Paisagem Elemental, Barreira Aberta, Domínio Absoluto;</li>
 *   <li>Choque de Domínios, Exaustão Mágica ao fim, domínios de mobs/chefes (via SpellCaster.castAsMob).</li>
 *   <li>v0.8: barreira com integridade e lados (quebra por fora; invertida, por dentro), registro no núcleo
 *       ({@code core/api/Domains}: leis de outros módulos), Lei Ruína, raios maiores.</li>
 * </ul>
 */
public class DomainModule implements Module {
    @Override
    public String id() {
        return "domain";
    }

    @Override
    public void init(IEventBus modBus) {
        DomainRegistries.register(modBus);
        DomainGlyphs.register();
        modBus.addListener((RegisterPayloadHandlersEvent e) -> DomainNet.register(e));
        DomainEvents.register();
        // v0.8: a integridade das barreiras mora no domo; o núcleo encaminha os golpes na parede para cá
        DomainBarrier.register();
    }
}
