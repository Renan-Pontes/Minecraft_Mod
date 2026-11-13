package com.renanpontes.isekaimahou.feature.ambience.client;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * SÓ CLIENTE. Uma faixa ambiente em loop na categoria MÚSICA (o controle "Música" do jogo manda nela), sem posição.
 * O fade vai de 0 a 1 e é multiplicado pelo volume da opção a cada tick — mexer na opção vale na hora.
 */
final class AmbientTrack extends AbstractTickableSoundInstance {
    final String zone;
    private float max;
    private float fade;
    private float target;
    private float step;

    AmbientTrack(String zone, SoundEvent event, float max) {
        super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.zone = zone;
        this.max = max;
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 0.0001F;
    }

    void setMax(float max) {
        this.max = max;
    }

    /** Sobe até o volume cheio em {@code ticks}. */
    void fadeIn(int ticks) {
        target = 1F;
        step = 1F / Math.max(1, ticks);
    }

    /** Desce até o silêncio em {@code ticks} e então para. */
    void fadeOut(int ticks) {
        target = 0F;
        step = Math.max(fade, 0.02F) / Math.max(1, ticks);
    }

    @Override
    public void tick() {
        if (fade < target) fade = Math.min(target, fade + step);
        else if (fade > target) fade = Math.max(target, fade - step);
        // curva suave (quadrática): o começo do fade não "pula" no ouvido
        volume = Math.max(0.0001F, fade * fade * max);
        if (target <= 0F && fade <= 0.001F) stop();
    }

    /** Pode começar muda (o fade in parte de ~0; sem isto o motor de som descarta a faixa). */
    @Override
    public boolean canStartSilent() {
        return true;
    }
}
