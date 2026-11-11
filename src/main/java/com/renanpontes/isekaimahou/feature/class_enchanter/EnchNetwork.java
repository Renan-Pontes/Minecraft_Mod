package com.renanpontes.isekaimahou.feature.class_enchanter;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_enchanter.rite.EnchanterRite;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do Encantador: o botão "Realizar Inscrição" da aba (cliente → servidor; o servidor confere tudo). */
public final class EnchNetwork {
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_enchanter-1");
        r.playToServer(RiteRequest.TYPE, RiteRequest.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) EnchanterRite.request(sp);
        }));
    }

    /** Cliente: pede a Inscrição do próximo rank. */
    public static void requestRite() {
        PacketDistributor.sendToServer(RiteRequest.INSTANCE);
    }

    public record RiteRequest() implements CustomPacketPayload {
        public static final RiteRequest INSTANCE = new RiteRequest();
        public static final Type<RiteRequest> TYPE = new Type<>(IsekaiMahou.id("enchanter_rite_request"));
        public static final StreamCodec<ByteBuf, RiteRequest> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private EnchNetwork() {}
}
