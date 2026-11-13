package com.renanpontes.isekaimahou.feature.space_magic.network;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: dimensão escolhida na tela do Abrir Portal (texto vazio = cancelou). */
public record GateSelectPayload(String dim) implements CustomPacketPayload {
    public static final Type<GateSelectPayload> TYPE = new Type<>(IsekaiMahou.id("space_magic_gate_select"));
    public static final StreamCodec<ByteBuf, GateSelectPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(128), GateSelectPayload::dim,
            GateSelectPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
