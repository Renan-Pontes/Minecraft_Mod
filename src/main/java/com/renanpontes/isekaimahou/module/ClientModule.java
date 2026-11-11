package com.renanpontes.isekaimahou.module;

import net.neoforged.bus.api.IEventBus;

/**
 * Parte de CLIENTE de um módulo (só existe no cliente). Chamada pelo {@code IsekaiMahouClient}
 * depois do núcleo do cliente. Aqui: renderers, camadas de HUD, telas/abas do menu, teclas, partículas.
 */
public interface ClientModule {
    void initClient(IEventBus modBus);
}
