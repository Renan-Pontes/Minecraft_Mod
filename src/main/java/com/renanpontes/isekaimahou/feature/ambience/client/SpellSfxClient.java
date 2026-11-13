package com.renanpontes.isekaimahou.feature.ambience.client;

import com.renanpontes.isekaimahou.feature.ambience.AmbienceConfig;
import com.renanpontes.isekaimahou.feature.ambience.AmbienceNet;
import com.renanpontes.isekaimahou.feature.ambience.AmbienceSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * SÓ CLIENTE. Toca o som de magia pedido pelo servidor, na posição dele, com o volume da opção do jogador.
 * Teto de sons novos por tick: uma rajada de pacotes não empilha dezenas de vozes iguais.
 */
public final class SpellSfxClient {
    private static final float[] CAST_VOLUME = {0.5F, 0.7F, 1.0F};
    private static final float[] IMPACT_VOLUME = {0.55F, 1.0F};
    private static final int MAX_PER_TICK = 4;

    private static long lastTick = Long.MIN_VALUE;
    private static int playedThisTick;

    public static void receive(AmbienceNet.Sfx p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !AmbienceConfig.on(AmbienceConfig.SPELL_SOUNDS)) return;
        float option = AmbienceConfig.volume(AmbienceConfig.SPELL_VOLUME);
        if (option <= 0F) return;
        long now = mc.level.getGameTime();
        if (now != lastTick) {
            lastTick = now;
            playedThisTick = 0;
        }
        if (playedThisTick++ >= MAX_PER_TICK) return;
        boolean impact = p.kind() == AmbienceSounds.KIND_IMPACT;
        int size = impact ? (p.size() > 0 ? 1 : 0) : Mth.clamp(p.size(), 0, 2);
        SoundEvent ev = AmbienceSounds.spell(p.kind(), AmbienceSounds.Family.byIndex(p.family()), size);
        float volume = (impact ? IMPACT_VOLUME[size] : CAST_VOLUME[size]) * option;
        mc.getSoundManager().play(new SimpleSoundInstance(ev, SoundSource.PLAYERS, volume, Mth.clamp(p.pitch(), 0.5F, 2F),
                SoundInstance.createUnseededRandom(), p.x(), p.y(), p.z()));
    }

    private SpellSfxClient() {}
}
