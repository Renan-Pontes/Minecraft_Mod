package com.renanpontes.isekaimahou.feature.las_noches;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Rede do módulo: o servidor conta ao cliente onde fica Las Noches (centro da cúpula) quando ele entra no Mundo Oco ou
 * quando a construção termina. O cliente usa para a música ambiente, o letreiro e as partículas do céu falso.
 */
public final class LnNet {
    /** Consumidor do cliente (nulo no servidor dedicado). */
    public static volatile Consumer<Info> clientInfo;

    /** {@code built} = Las Noches pronta; {@code center} = centro da cúpula (nível do piso). */
    public record Info(boolean built, BlockPos center) implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(IsekaiMahou.id("las_noches_info"));
        public static final StreamCodec<FriendlyByteBuf, Info> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Info::built, BlockPos.STREAM_CODEC, Info::center, Info::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("las_noches-1").playToClient(Info.TYPE, Info.STREAM_CODEC, (p, c) -> {
            Consumer<Info> h = clientInfo;
            if (h != null) h.accept(p);
        });
    }

    static void send(ServerPlayer sp, LnData d) {
        PacketDistributor.sendToPlayer(sp, new Info(d.built(), d.center != null ? d.center : BlockPos.ZERO));
    }

    private LnNet() {}
}
