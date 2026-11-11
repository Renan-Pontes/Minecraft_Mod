package com.renanpontes.isekaimahou.core.boss;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Kit de chefes do núcleo (v0.7): telegrafia no chão ({@link Telegraph}), escala de grupo, anti-trapaça e tremor de
 * câmera ({@link BossKit}) e recompensas de grupo ({@link BossRewards}). {@link #init} é chamado pelo construtor do
 * mod (lado comum); a parte de desenho fica em {@code client/boss/BossToolkitClient}.
 */
public final class BossToolkit {
    public static void init(IEventBus modBus) {
        modBus.addListener(BossPayloads::register);
        NeoForge.EVENT_BUS.addListener(BossScheduler::onServerTick);
        NeoForge.EVENT_BUS.addListener(BossScheduler::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(BossScheduler::onServerStopped);
        NeoForge.EVENT_BUS.addListener(BossKit::onFall);
        NeoForge.EVENT_BUS.addListener(BossRewards::onDamage);
        BossCommands.register();
    }

    private BossToolkit() {}
}
