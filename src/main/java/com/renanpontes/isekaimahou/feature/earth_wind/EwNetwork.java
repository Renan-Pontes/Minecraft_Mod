package com.renanpontes.isekaimahou.feature.earth_wind;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do modulo earth_wind. */
public final class EwNetwork {
    /** Gancho de cliente para o tremor de tela (o cliente do modulo substitui; no servidor nunca e chamado). */
    public static Consumer<Shake> clientShake = p -> {};

    /** C -> S: o jogador usou um pulo extra no ar (Pulo Duplo). */
    public record DoubleJump() implements CustomPacketPayload {
        public static final Type<DoubleJump> TYPE = new Type<>(IsekaiMahou.id("earth_wind_double_jump"));
        public static final StreamCodec<ByteBuf, DoubleJump> STREAM_CODEC = StreamCodec.unit(new DoubleJump());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** S -> C: tremor de tela (Tremor, Fenda, pancada do Golem). */
    public record Shake(float intensity, int ticks) implements CustomPacketPayload {
        public static final Type<Shake> TYPE = new Type<>(IsekaiMahou.id("earth_wind_shake"));
        public static final StreamCodec<ByteBuf, Shake> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Shake::intensity,
                ByteBufCodecs.VAR_INT, Shake::ticks,
                Shake::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("earth_wind-1");
        r.playToServer(DoubleJump.TYPE, DoubleJump.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) EwEvents.onDoubleJump(sp);
        });
        r.playToClient(Shake.TYPE, Shake.STREAM_CODEC, (p, c) -> clientShake.accept(p));
    }

    private EwNetwork() {}
}
