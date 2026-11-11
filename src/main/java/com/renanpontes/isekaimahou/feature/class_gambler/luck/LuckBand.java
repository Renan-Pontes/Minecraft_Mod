package com.renanpontes.isekaimahou.feature.class_gambler.luck;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Faixas da rolagem de sorte do Apostador (de −99% a +100% de poder). O peso base decide a chance; a Sorte do jogador
 * empurra o peso das faixas ruins para baixo e o das boas para cima ({@link LuckService#weights}).
 * {@code fever} = quanto a Tensão sobe (azar acumula tensão; a tensão cheia garante o JACKPOT).
 */
public enum LuckBand {
    //        mult   peso  bom?   tensão cor
    BUST(0.01F, 5, false, 12, 0x6A6478),
    BAD(0.50F, 12, false, 8, 0xC0504A),
    LOW(0.80F, 18, false, 4, 0xD89A5A),
    EVEN(1.00F, 26, false, 3, 0xD8D2E4),
    GOOD(1.25F, 19, true, 2, 0x6FD88A),
    GREAT(1.50F, 13, true, 2, 0x5AC8FF),
    NATURAL(2.00F, 7, true, 1, 0xFFC94A),
    /** Não sai no sorteio de pesos: é a chance própria de Jackpot (ou a Tensão cheia). */
    JACKPOT(2.00F, 0, true, 0, 0xFF4AD2);

    public static final LuckBand[] VALUES = values();

    public final float mult;
    public final int weight;
    public final boolean good;
    public final int fever;
    public final int rgb;

    LuckBand(float mult, int weight, boolean good, int fever, int rgb) {
        this.mult = mult;
        this.weight = weight;
        this.good = good;
        this.fever = fever;
        this.rgb = rgb;
    }

    /** Faixa que conta como "ruim" para a Sorte (o peso cai com a sorte). EVEN é neutra. */
    public boolean bad() {
        return this == BUST || this == BAD || this == LOW;
    }

    /** Variação em porcentagem a partir do multiplicador ("+50", "-99"). */
    public static int percent(float mult) {
        return Math.round((mult - 1F) * 100F);
    }

    public MutableComponent displayName() {
        return Component.translatable(String.join("", "luck.isekai_mahou.band.", name().toLowerCase(java.util.Locale.ROOT)));
    }

    public static LuckBand byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : EVEN;
    }
}
