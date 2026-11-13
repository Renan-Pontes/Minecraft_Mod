package com.renanpontes.isekaimahou.feature.boss_music;

import com.renanpontes.isekaimahou.module.Module;

import net.neoforged.bus.api.IEventBus;

/**
 * Módulo "boss_music" (v1.3): músicas tema dos chefes. Do lado comum só registra os sons e o comando de teste;
 * a escolha do tema, os fades e a vinheta de vitória são do cliente ({@code client/BossMusicPlayer}).
 */
public class BossMusicModule implements Module {
    @Override
    public String id() {
        return "boss_music";
    }

    @Override
    public void init(IEventBus modBus) {
        BossMusicSounds.register(modBus);
        BossMusicNet.register(modBus);
    }
}
