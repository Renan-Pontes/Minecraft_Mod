package com.renanpontes.isekaimahou.feature.paths;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do módulo Caminhos. O progresso em si chega ao cliente pela sincronização do MagicData. */
public final class PathsNetwork {

    /** Cliente → servidor: "despertar este elemento pela afinidade da minha classe" (botão da aba). */
    public record AwakenAffinity(String element) implements CustomPacketPayload {
        public static final Type<AwakenAffinity> TYPE = new Type<>(IsekaiMahou.id("paths_awaken_affinity"));
        public static final StreamCodec<ByteBuf, AwakenAffinity> STREAM_CODEC =
                ByteBufCodecs.stringUtf8(32).map(AwakenAffinity::new, AwakenAffinity::element);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("paths-1");
        r.playToServer(AwakenAffinity.TYPE, AwakenAffinity.STREAM_CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) PathProgress.awakenByAffinity(sp, Element.byId(p.element()));
        });
    }

    private PathsNetwork() {}
}
