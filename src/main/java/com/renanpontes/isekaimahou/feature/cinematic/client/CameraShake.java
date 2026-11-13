package com.renanpontes.isekaimahou.feature.cinematic.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Tremor de tela (só cliente). API reutilizável por qualquer código de CLIENTE:
 * <pre>CameraShake.add(1.0F, 12);                    // tremor médio por 12 ticks
 * CameraShake.addAt(pos, 2.0F, 20, 24);           // explosão: mais fraco quanto mais longe (raio 24)</pre>
 * A intensidade é aproximadamente "graus" de desvio da câmera (1 = leve, 2 = forte, 3+ = violento).
 * Respeita a opção de acessibilidade "Efeitos de distorção" (screenEffectScale) do jogo.
 */
public final class CameraShake {
    private static final int MAX_SHAKES = 16;
    private static final float MAX_TOTAL = 4.5F;
    private static final List<Shake> SHAKES = new ArrayList<>();
    private static final RandomSource RNG = RandomSource.create();

    /** Adiciona um tremor que some gradualmente em {@code ticks}. */
    public static void add(float intensity, int ticks) {
        if (!(intensity > 0) || ticks <= 0) return;
        if (SHAKES.size() >= MAX_SHAKES) SHAKES.remove(0);
        SHAKES.add(new Shake(Math.min(intensity, MAX_TOTAL), ticks,
                RNG.nextFloat() * Mth.TWO_PI, RNG.nextFloat() * Mth.TWO_PI, RNG.nextFloat() * Mth.TWO_PI));
    }

    /** Tremor vindo de um ponto do mundo: cai com a distância até o jogador (zero fora do raio). */
    public static void addAt(Vec3 pos, float intensity, int ticks, double radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || radius <= 0) return;
        double d = mc.player.position().distanceTo(pos);
        if (d >= radius) return;
        float f = (float) (1.0 - d / radius);
        add(intensity * f * f, ticks);
    }

    /** Há algum tremor ativo? */
    public static boolean active() {
        return !SHAKES.isEmpty();
    }

    static void tick() {
        if (Minecraft.getInstance().isPaused()) return;
        SHAKES.removeIf(s -> ++s.age >= s.ticks);
    }

    static void clear() {
        SHAKES.clear();
    }

    /**
     * Desvio atual em graus: out[0] = yaw, out[1] = pitch, out[2] = roll.
     * Soma de senos com fases aleatórias: suave (sem "teleportes" da câmera) e diferente a cada tremor.
     */
    static void offsets(float partialTick, float[] out) {
        out[0] = out[1] = out[2] = 0F;
        if (SHAKES.isEmpty()) return;
        float scale = Minecraft.getInstance().options.screenEffectScale().get().floatValue() * com.renanpontes.isekaimahou.core.MahouConfig.shakeScale();
        if (scale <= 0F) return;
        float total = 0F;
        for (Shake s : SHAKES) {
            float t = s.age + partialTick;
            float life = Mth.clamp(1F - t / s.ticks, 0F, 1F);
            float amp = s.intensity * life * life;
            if (total + amp > MAX_TOTAL) amp = Math.max(0F, MAX_TOTAL - total);
            total += amp;
            out[0] += amp * 1.5F * (Mth.sin(t * 1.9F + s.phaseA) * 0.6F + Mth.sin(t * 3.7F + s.phaseB) * 0.4F);
            out[1] += amp * 1.1F * (Mth.sin(t * 2.3F + s.phaseB) * 0.6F + Mth.sin(t * 4.9F + s.phaseC) * 0.4F);
            out[2] += amp * 0.9F * Mth.sin(t * 1.3F + s.phaseC);
        }
        out[0] *= scale;
        out[1] *= scale;
        out[2] *= scale;
    }

    private static final class Shake {
        final float intensity;
        final int ticks;
        final float phaseA, phaseB, phaseC;
        int age;

        Shake(float intensity, int ticks, float phaseA, float phaseB, float phaseC) {
            this.intensity = intensity;
            this.ticks = ticks;
            this.phaseA = phaseA;
            this.phaseB = phaseB;
            this.phaseC = phaseC;
        }
    }

    private CameraShake() {}
}
