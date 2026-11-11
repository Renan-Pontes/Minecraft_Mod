package com.renanpontes.isekaimahou.module;

import net.neoforged.bus.api.IEventBus;

/**
 * Um módulo do mod (lado comum). Chamado no construtor do mod, ANTES dos registros acontecerem.
 * No {@link #init} o módulo deve:
 * <ul>
 *   <li>registrar os seus DeferredRegister no {@code modBus} (itens, blocos, entidades...);</li>
 *   <li>registrar glifos em {@code GlyphRegistry};</li>
 *   <li>adicionar listeners (mod bus ou NeoForge.EVENT_BUS), pacotes de rede, subcomandos /mahou, itens de kit.</li>
 * </ul>
 * NÃO coloque código de cliente aqui (use a classe {@code client/...Client}).
 */
public interface Module {
    String id();

    void init(IEventBus modBus);
}
