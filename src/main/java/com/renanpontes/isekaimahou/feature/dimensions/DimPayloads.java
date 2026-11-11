package com.renanpontes.isekaimahou.feature.dimensions;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do módulo: abrir a tela do Altar (servidor → cliente) e pedir a Convergência (cliente → servidor). */
public final class DimPayloads {

    /** Abre a tela do Ritual de Convergência para o altar em {@code pos}. */
    public record OpenAltar(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenAltar> TYPE = new Type<>(IsekaiMahou.id("dimensions_open_altar"));
        public static final StreamCodec<ByteBuf, OpenAltar> STREAM_CODEC = BlockPos.STREAM_CODEC.map(OpenAltar::new, OpenAltar::pos);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Pedido de fusão dos elementos base {@code a} e {@code b} no altar em {@code pos} (o servidor valida tudo de novo). */
    public record Converge(BlockPos pos, String a, String b) implements CustomPacketPayload {
        public static final Type<Converge> TYPE = new Type<>(IsekaiMahou.id("dimensions_converge"));
        public static final StreamCodec<ByteBuf, Converge> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Converge::pos, ByteBufCodecs.STRING_UTF8, Converge::a, ByteBufCodecs.STRING_UTF8, Converge::b,
                Converge::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("dimensions-1");
        // O handler do cliente só chama o gancho (preenchido pela parte de cliente): seguro no servidor dedicado.
        r.playToClient(OpenAltar.TYPE, OpenAltar.STREAM_CODEC, (p, c) -> DimClientBridge.openAltar.accept(p.pos()));
        r.playToServer(Converge.TYPE, Converge.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) {
                Convergence.perform(sp, p.pos(), Element.byId(p.a()), Element.byId(p.b()));
            }
        });
    }

    private DimPayloads() {}
}
