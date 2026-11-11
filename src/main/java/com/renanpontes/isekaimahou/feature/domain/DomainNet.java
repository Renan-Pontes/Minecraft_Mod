package com.renanpontes.isekaimahou.feature.domain;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede do módulo: tremor de tela (Cataclismo, choque de domínios) e o soco na parede da barreira (cliente → servidor).
 * O handler chama um gancho substituível que a parte de cliente preenche — assim nenhuma classe de
 * cliente é carregada no servidor dedicado.
 */
public final class DomainNet {

    /** Tremor de câmera: força (graus aproximados) e duração em ticks. */
    public record Shake(float strength, int ticks) implements CustomPacketPayload {
        public static final Type<Shake> TYPE = new Type<>(IsekaiMahou.id("domain_shake"));
        public static final StreamCodec<ByteBuf, Shake> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Shake::strength,
                ByteBufCodecs.VAR_INT, Shake::ticks,
                Shake::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Soco no ar mirando a parede de um domo (o servidor valida distância, mira e recarga). */
    public record BarrierPunch(int domeId) implements CustomPacketPayload {
        public static final Type<BarrierPunch> TYPE = new Type<>(IsekaiMahou.id("domain_barrier_punch"));
        public static final StreamCodec<ByteBuf, BarrierPunch> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BarrierPunch::domeId,
                BarrierPunch::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Preenchido pelo DomainClient. No servidor dedicado nunca é chamado. */
    public static volatile Consumer<Shake> clientShake = p -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("domain-1");
        r.playToClient(Shake.TYPE, Shake.STREAM_CODEC, (p, c) -> clientShake.accept(p));
        r.playToServer(BarrierPunch.TYPE, BarrierPunch.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) DomainBarrier.punch(sp, p.domeId());
        }));
    }

    private DomainNet() {}
}
