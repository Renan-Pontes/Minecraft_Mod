package com.renanpontes.isekaimahou.feature.world_events;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do módulo: só o estado do evento (para o céu e as partículas do cliente). */
public final class WeNetwork {
    /** Servidor → cliente: evento ativo onde o jogador está ({@code event} = ordinal do {@link WorldEventType}, -1 = nenhum). */
    public record State(int event) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(IsekaiMahou.id("world_events_state"));
        public static final StreamCodec<FriendlyByteBuf, State> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(State::new, State::event).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("world_events-1").optional();
        r.playToClient(State.TYPE, State.STREAM_CODEC, (p, c) -> c.enqueueWork(
                () -> com.renanpontes.isekaimahou.feature.world_events.client.WeClientState.apply(p.event())));
    }

    private WeNetwork() {}
}
