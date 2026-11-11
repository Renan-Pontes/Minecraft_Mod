package com.renanpontes.isekaimahou.feature.equipment.network;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor -> cliente: um jogador começou uma animação de conjuração (para quem o vê e para ele mesmo;
 * no próprio jogador a canalização começa antes pelo CastState do núcleo, e este pacote só confirma/ajusta a cor).
 *
 * @param entityId id do jogador que conjura
 * @param kind     {@link #QUICK}, {@link #CHANNEL_START}, {@link #CHANNEL_STOP}, {@link #ULTIMATE} ou {@link #SELF}
 * @param rgb      cor da magia (partículas nas mãos)
 */
public record CastAnimPayload(int entityId, byte kind, int rgb) implements CustomPacketPayload {
    public static final byte QUICK = 0;
    public static final byte CHANNEL_START = 1;
    public static final byte CHANNEL_STOP = 2;
    public static final byte ULTIMATE = 3;
    public static final byte SELF = 4;

    public static final Type<CastAnimPayload> TYPE = new Type<>(IsekaiMahou.id("equipment_cast_anim"));
    public static final StreamCodec<ByteBuf, CastAnimPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CastAnimPayload::entityId,
            ByteBufCodecs.BYTE, CastAnimPayload::kind,
            ByteBufCodecs.INT, CastAnimPayload::rgb,
            CastAnimPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
