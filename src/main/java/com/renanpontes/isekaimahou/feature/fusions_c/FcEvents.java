package com.renanpontes.isekaimahou.feature.fusions_c;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Eventos de jogo das Misturas C: roda as sequências e limpa o estado estático quando o servidor para. */
final class FcEvents {

    static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> FcTasks.tick());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            FcTasks.clear();
            FcUtil.clear();
        });
    }

    private FcEvents() {}
}
