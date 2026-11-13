package com.renanpontes.isekaimahou.feature.arcana_items;

import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "arcana_items" — v1.0: itens arcanos de conveniência, todos com custo real.
 * <ul>
 *   <li><b>Colar de Mana</b> (3 níveis): guarda a mana que sobraria e é gasto antes da barra;</li>
 *   <li><b>Pergaminho de Receita</b>: grava uma magia para um amigo — caro de fazer, uso único;</li>
 *   <li><b>Bolsa do Alquimista</b>: só essências/cristais/reagentes, só classes Mágicas;</li>
 *   <li><b>Pergaminho de Retorno</b>: volta à última cidade visitada, fora de combate.</li>
 * </ul>
 * O glifo Recuperar Pertences e o envio do loot de chefe para o Inventário Dimensional ficam no módulo space_magic.
 */
public class ArcanaItemsModule implements Module {
    @Override
    public String id() {
        return "arcana_items";
    }

    @Override
    public void init(IEventBus modBus) {
        ArcanaContent.register(modBus);
        ArcanaEvents.register();
        ArcanaCommands.register();
        modBus.addListener(ArcanaNet::register);
    }
}
