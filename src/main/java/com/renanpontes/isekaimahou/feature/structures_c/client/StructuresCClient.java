package com.renanpontes.isekaimahou.feature.structures_c.client;

import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;

/**
 * Parte de cliente do módulo "structures_c". As estruturas não têm renderer, tela nem HUD próprios: os
 * avisos de descoberta usam os pacotes de título do vanilla (enviados pelo servidor) e as criaturas que
 * moram nelas são desenhadas pelos módulos de criaturas. Por isso não há nada a registrar aqui.
 */
public class StructuresCClient implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        // Intencionalmente vazio: ver o comentário da classe.
    }
}
