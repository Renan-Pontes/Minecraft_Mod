package com.renanpontes.isekaimahou.client.fx;

import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;

/**
 * SÓ CLIENTE. Preset gráfico do mod (config {@code graphicsPreset}; "reducedParticles" antigo = no máximo MEDIUM).
 * Todo efeito visual novo deve passar por aqui: {@link #count(int)} para quantidades de partículas,
 * {@link #atLeast(Graphics)} para extras caros (brilhos, rastros, luzes, camadas), {@link #range()} para a distância
 * máxima em que efeitos de outras entidades são desenhados.
 */
public final class FxQuality {
    public static Graphics level() {
        Graphics g = MahouConfig.safe(MahouConfig.GRAPHICS);
        if (MahouConfig.safe(MahouConfig.REDUCED_PARTICLES) && g.ordinal() > Graphics.MEDIUM.ordinal()) return Graphics.MEDIUM;
        return g;
    }

    /** Multiplicador de partículas: 0,3 / 0,6 / 1 / 1,5. */
    public static float particles() {
        return switch (level()) {
            case LOW -> 0.3F;
            case MEDIUM -> 0.6F;
            case HIGH -> 1.0F;
            case ULTRA -> 1.5F;
        };
    }

    /** Quantidade ajustada ao preset (nunca menos que 1 se base > 0). */
    public static int count(int base) {
        if (base <= 0) return 0;
        return Math.max(1, Math.round(base * particles()));
    }

    public static boolean atLeast(Graphics g) {
        return level().ordinal() >= g.ordinal();
    }

    /** Distância (blocos) até onde efeitos de outros são desenhados: 24 / 40 / 64 / 96. */
    public static double range() {
        return switch (level()) {
            case LOW -> 24;
            case MEDIUM -> 40;
            case HIGH -> 64;
            case ULTRA -> 96;
        };
    }

    /** A distância ao quadrado (da câmera/jogador até o efeito) está dentro de {@link #range()}? */
    public static boolean inRange(double distSqr) {
        double r = range();
        return distSqr <= r * r;
    }

    /** Multiplicador de tremor de câmera/flash de tela: PC fraco treme menos (0,5 / 0,75 / 1 / 1). */
    public static float shake() {
        return switch (level()) {
            case LOW -> 0.5F;
            case MEDIUM -> 0.75F;
            case HIGH, ULTRA -> 1.0F;
        };
    }

    private FxQuality() {}
}
