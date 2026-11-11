package com.renanpontes.isekaimahou.feature.arcane.network;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor -> cliente: minérios encontrados pelo glifo Detectar (posição + cor RGB de cada um) e por quantos ticks
 * o cliente deve destacá-los através das paredes.
 */
public record OreRevealPayload(List<BlockPos> positions, List<Integer> colors, int ticks) implements CustomPacketPayload {
    public static final int MAX = 1024;
    public static final Type<OreRevealPayload> TYPE = new Type<>(IsekaiMahou.id("arcane_ore_reveal"));
    public static final StreamCodec<ByteBuf, OreRevealPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX)), OreRevealPayload::positions,
            ByteBufCodecs.INT.apply(ByteBufCodecs.list(MAX)), OreRevealPayload::colors,
            ByteBufCodecs.VAR_INT, OreRevealPayload::ticks,
            OreRevealPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
