package com.renanpontes.isekaimahou.feature.espada;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacote único de efeitos dos Espada (servidor → cliente): o servidor só diz "o quê e onde"; o cliente monta a
 * coreografia (partículas, feixes, clarões, tremor, sons) de acordo com o preset gráfico ({@code FxQuality}).
 */
public final class EspadaPayloads {
    /** Gancho preenchido pela parte de cliente (no servidor dedicado não faz nada). */
    public static volatile Consumer<Fx> onFx = p -> {};

    /** Distância até onde os jogadores recebem os efeitos. */
    public static final double RANGE = 96.0;

    /**
     * @param kind   tipo do efeito ({@code EspadaFx.*})
     * @param entity entidade de referência (ou -1)
     * @param a      ponto principal (origem / centro)
     * @param b      segundo ponto (destino) ou direção
     * @param p1     parâmetro 1 (raio, largura, ticks...)
     * @param p2     parâmetro 2
     * @param color  cor RGB principal
     * @param aux    variação (estilo)
     */
    public record Fx(int kind, int entity, Vec3 a, Vec3 b, float p1, float p2, int color, int aux) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(IsekaiMahou.id("espada_fx"));
        public static final StreamCodec<FriendlyByteBuf, Fx> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeByte(p.kind);
            buf.writeVarInt(p.entity + 1);
            buf.writeDouble(p.a.x);
            buf.writeDouble(p.a.y);
            buf.writeDouble(p.a.z);
            buf.writeFloat((float) p.b.x);
            buf.writeFloat((float) p.b.y);
            buf.writeFloat((float) p.b.z);
            buf.writeFloat(p.p1);
            buf.writeFloat(p.p2);
            buf.writeInt(p.color);
            buf.writeVarInt(p.aux);
        }, buf -> new Fx(buf.readByte(), buf.readVarInt() - 1, new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("espada-1");
        r.playToClient(Fx.TYPE, Fx.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> onFx.accept(p)));
    }

    public static void send(ServerLevel level, Fx fx) {
        PacketDistributor.sendToPlayersNear(level, null, fx.a().x, fx.a().y, fx.a().z, RANGE, fx);
    }

    private EspadaPayloads() {}
}
