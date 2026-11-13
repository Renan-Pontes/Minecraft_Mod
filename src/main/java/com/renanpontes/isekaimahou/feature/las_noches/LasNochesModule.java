package com.renanpontes.isekaimahou.feature.las_noches;

import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "las_noches" (v1.3): o antigo Palácio da Noite Eterna (id {@code hollow_palace}) vira o Portão de LAS NOCHES e
 * ganha, ao norte, a cúpula colossal com o céu falso de Aizen, a torre central (no terraço da cúpula, a arena da Segunda
 * Etapa), corredores brancos, as salas numeradas das Espada (2 Barragan, 4 Ulquiorra, 6 Grimmjow — invocados por id do
 * módulo espada), a Sala do Trono vazia e guardas (Exequias, Fracciones, Menos no pátio). Construída em código, aos
 * poucos ({@link LnBuilder}); ver {@link LnLayout}.
 */
public class LasNochesModule implements Module {
    @Override
    public String id() {
        return "las_noches";
    }

    @Override
    public void init(IEventBus modBus) {
        modBus.addListener(LnNet::register);
        LnEvents.register();
        LnCommands.register();
        BossArena.protectPlace(LnEvents.DOME_PLACE);
    }
}
