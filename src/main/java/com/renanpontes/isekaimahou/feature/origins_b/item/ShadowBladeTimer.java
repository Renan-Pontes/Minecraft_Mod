package com.renanpontes.isekaimahou.feature.origins_b.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Componente da Lâmina Sombria: ticks restantes, o total (para a barra) e o instante absoluto (tempo de jogo)
 * em que ela se desfaz. O instante absoluto impede "guardar" a lâmina num baú/funil/moldura para pausar o tempo:
 * ao voltar para um inventário ela já está vencida. {@code expires = 0} = ainda não ancorada ao relógio do mundo.
 */
public record ShadowBladeTimer(int left, int total, long expires) {
    public static final Codec<ShadowBladeTimer> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("left").forGetter(ShadowBladeTimer::left),
            Codec.INT.fieldOf("total").forGetter(ShadowBladeTimer::total),
            Codec.LONG.optionalFieldOf("expires", 0L).forGetter(ShadowBladeTimer::expires)
    ).apply(i, ShadowBladeTimer::new));

    public static final StreamCodec<ByteBuf, ShadowBladeTimer> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShadowBladeTimer::left,
            ByteBufCodecs.VAR_INT, ShadowBladeTimer::total,
            ByteBufCodecs.VAR_LONG, ShadowBladeTimer::expires,
            ShadowBladeTimer::new);

    public float fraction() {
        return total <= 0 ? 0F : Math.max(0F, Math.min(1F, left / (float) total));
    }
}
