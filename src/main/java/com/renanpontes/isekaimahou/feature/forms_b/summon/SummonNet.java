package com.renanpontes.isekaimahou.feature.forms_b.summon;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes da Forja de Invocações: a tecla de ordem (cliente → servidor) e a cor do elemento do corpo (servidor → cliente). */
public final class SummonNet {
    /** Cliente → servidor: o jogador apertou a tecla de ordem (passa para a próxima ordem em todas as invocações dele). */
    public record OrderKey() implements CustomPacketPayload {
        public static final Type<OrderKey> TYPE = new Type<>(IsekaiMahou.id("forms_b_summon_order"));
        public static final StreamCodec<FriendlyByteBuf, OrderKey> STREAM_CODEC = StreamCodec.unit(new OrderKey());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Servidor → cliente: cor (RGB) com que o corpo {@code entityId} aparece; {@code -1} = cor natural. */
    public record Look(int entityId, int color) implements CustomPacketPayload {
        public static final Type<Look> TYPE = new Type<>(IsekaiMahou.id("forms_b_summon_look"));
        public static final StreamCodec<FriendlyByteBuf, Look> STREAM_CODEC = StreamCodec.of((buf, l) -> {
            buf.writeVarInt(l.entityId);
            buf.writeInt(l.color);
        }, buf -> new Look(buf.readVarInt(), buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Gancho preenchido pela parte de cliente (no servidor dedicado continua vazio). */
    public static Consumer<Look> clientLook = l -> {};

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("forms_b-1");
        r.playToServer(OrderKey.TYPE, OrderKey.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) SummonForge.cycleOrder(sp);
        });
        r.playToClient(Look.TYPE, Look.STREAM_CODEC, (l, c) -> clientLook.accept(l));
    }

    private SummonNet() {}
}
