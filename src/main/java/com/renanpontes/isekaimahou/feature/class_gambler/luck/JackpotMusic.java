package com.renanpontes.isekaimahou.feature.class_gambler.luck;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * A trilha do JACKPOT, tocada com blocos musicais (composição própria, em Fá# maior):
 * <ul>
 *   <li><b>Cassino</b> (Jackpot comum): arpejo animado I–V–vi–IV com baixo e chimbal, uma "semicolcheia" a cada 3 ticks;</li>
 *   <li><b>Dança da Morte Ociosa</b> (Jackpot Imortal do Domínio): mais rápida (a cada 2 ticks), gancho sincopado,
 *   baixo pulsando, bumbo e palmas — a festa de quem não pode morrer.</li>
 * </ul>
 * Notas em semitons a partir do Fá# grave do bloco musical (0..24 = afinação 0,5..2,0); -1 = pausa.
 */
public final class JackpotMusic {
    /** Ticks por passo de cada trilha. */
    public static final int CASINO_STEP = 3;
    public static final int DANCE_STEP = 2;

    // I (F#) – V (C#) – vi (D#m) – IV (B): arpejo subindo e descendo, 8 passos por acorde
    private static final int[] CASINO_MELODY = {
            12, 16, 19, 24, 19, 16, 12, 16,
            11, 14, 19, 23, 19, 14, 11, 14,
            9, 12, 16, 21, 16, 12, 9, 12,
            9, 12, 17, 21, 17, 21, 24, -1};
    private static final int[] CASINO_BASS = {0, 7, 9, 5};

    // gancho sincopado (a "dança" do jackpot imortal)
    private static final int[] DANCE_MELODY = {
            12, -1, 12, 15, -1, 17, -1, 15, 12, -1, 10, 12, -1, 15, 17, -1,
            19, -1, 19, 17, -1, 15, -1, 12, 15, -1, 17, -1, 15, 12, 10, 12,
            24, -1, 22, 19, -1, 17, -1, 19, 22, -1, 19, 17, -1, 15, 17, -1,
            19, -1, 17, 15, -1, 12, -1, 15, 17, 19, 22, -1, 24, -1, 24, -1};
    private static final int[] DANCE_BASS = {0, 0, 8, 8, 3, 3, 10, 10};

    private static float pitch(int n) {
        return (float) Math.pow(2.0, (n - 12) / 12.0);
    }

    private static void note(ServerLevel level, ServerPlayer sp, SoundEvent sound, float volume, int n) {
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), sound, SoundSource.PLAYERS, volume, pitch(Math.max(0, Math.min(24, n))));
    }

    /** Toca o passo da trilha para a idade {@code age} (ticks) do Jackpot. */
    public static void play(ServerLevel level, ServerPlayer sp, long age, boolean dance) {
        int stepTicks = dance ? DANCE_STEP : CASINO_STEP;
        if (age % stepTicks != 0) return;
        int step = (int) (age / stepTicks);
        if (dance) {
            int i = Math.floorMod(step, DANCE_MELODY.length);
            int n = DANCE_MELODY[i];
            if (n >= 0) note(level, sp, i % 16 == 0 ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.NOTE_BLOCK_PLING.value(), 0.55F, n);
            if (step % 2 == 0) note(level, sp, SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, DANCE_BASS[Math.floorMod(step / 8, DANCE_BASS.length)] + 12);
            if (step % 4 == 0) note(level, sp, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.7F, 6);
            if (step % 8 == 4) note(level, sp, SoundEvents.NOTE_BLOCK_SNARE.value(), 0.6F, 12);
            if (step % 2 == 1) note(level, sp, SoundEvents.NOTE_BLOCK_HAT.value(), 0.3F, 18);
            if (i % 16 == 0) note(level, sp, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.4F, n >= 0 ? n : 12);
        } else {
            int i = Math.floorMod(step, CASINO_MELODY.length);
            int n = CASINO_MELODY[i];
            if (n >= 0) note(level, sp, i % 8 == 0 ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.NOTE_BLOCK_PLING.value(), 0.5F, n);
            if (step % 4 == 0) note(level, sp, SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, CASINO_BASS[Math.floorMod(step / 8, CASINO_BASS.length)] + 12);
            if (step % 2 == 1) note(level, sp, SoundEvents.NOTE_BLOCK_HAT.value(), 0.25F, 16);
        }
    }

    private JackpotMusic() {}
}
