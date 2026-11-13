package com.renanpontes.isekaimahou.feature.space_magic.network;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Servidor -> cliente: destinos possíveis do Abrir Portal (ids de dimensão) e a cor da magia, para a tela de escolha. */
public record GateChoicePayload(List<String> dims, int color) implements CustomPacketPayload {
    public static final Type<GateChoicePayload> TYPE = new Type<>(IsekaiMahou.id("space_magic_gate_choice"));
    public static final StreamCodec<ByteBuf, GateChoicePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), GateChoicePayload::dims,
            ByteBufCodecs.INT, GateChoicePayload::color,
            GateChoicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
