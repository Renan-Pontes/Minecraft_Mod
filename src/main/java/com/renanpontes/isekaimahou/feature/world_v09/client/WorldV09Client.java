package com.renanpontes.isekaimahou.feature.world_v09.client;

import com.renanpontes.isekaimahou.module.ClientModule;

import net.neoforged.bus.api.IEventBus;

/**
 * Parte de cliente do módulo "world_v09". Não precisa de código: o Aglomerado de Cristal de Mana usa
 * {@code render_type: cutout} no próprio modelo JSON, as faíscas dos blocos saem do {@code animateTick} e o
 * letreiro de entrada das estruturas é o do núcleo ({@code Places}).
 */
public class WorldV09Client implements ClientModule {
    @Override
    public void initClient(IEventBus modBus) {
        // Nada a registrar no cliente (ver javadoc).
    }
}
