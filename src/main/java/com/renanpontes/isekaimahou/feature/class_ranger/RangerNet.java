package com.renanpontes.isekaimahou.feature.class_ranger;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do módulo (cliente → servidor): concluir um rank da Trilha do Ranger. */
public final class RangerNet {
    public record ClaimRank(int rank) implements CustomPacketPayload {
        public static final Type<ClaimRank> TYPE = new Type<>(IsekaiMahou.id("class_ranger_claim"));
        public static final StreamCodec<ByteBuf, ClaimRank> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(ClaimRank::new, ClaimRank::rank);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("class_ranger-1");
        r.playToServer(ClaimRank.TYPE, ClaimRank.STREAM_CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) RangerService.claim(sp, Math.max(1, Math.min(RangerProgress.MAX_RANK, msg.rank())));
        }));
    }

    private RangerNet() {}
}
