package com.renanpontes.isekaimahou.feature.boss_music.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * SÓ CLIENTE. Uma faixa tema em loop na categoria MÚSICA (o controle "Música" do jogo manda nela), sem posição.
 * O volume é ajustado a cada tick pelo próprio som (fade in/out lineares); ao chegar a zero num fade out, para sozinha.
 */
final class BossTrack extends AbstractTickableSoundInstance {
    final String theme;
    private final float max;
    private float target;
    private float step;

    BossTrack(String theme, SoundEvent event, float max) {
        super(event, SoundSource.MUSIC, RandomSource.create());
        this.theme = theme;
        this.max = max;
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.0001F;
        this.target = 0;
    }

    /** Sobe até o volume cheio em {@code ticks}. */
    void fadeIn(int ticks) {
        target = max;
        step = max / Math.max(1, ticks);
    }

    /** Desce até o silêncio em {@code ticks} e então para. */
    void fadeOut(int ticks) {
        target = 0;
        step = Math.max(volume, 0.01F) / Math.max(1, ticks);
    }

    boolean fadingOut() {
        return target <= 0;
    }

    @Override
    public void tick() {
        if (volume < target) volume = Math.min(target, volume + step);
        else if (volume > target) volume = Math.max(target, volume - step);
        if (target <= 0 && volume <= 0.0005F) stop();
    }

    /** Pode começar mudo (o fade in parte de ~0; sem isto o motor de som descarta a faixa). */
    @Override
    public boolean canStartSilent() {
        return true;
    }

    void kill() {
        stop();
    }
}
