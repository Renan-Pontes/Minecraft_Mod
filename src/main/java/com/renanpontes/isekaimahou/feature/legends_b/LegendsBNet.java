package com.renanpontes.isekaimahou.feature.legends_b;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede do módulo: um único pacote servidor → cliente com efeitos visuais curtos desenhados de verdade
 * (feixes estilo farol das Lanças do Céu, riscos brancos da Tempestade de Cortes, ondas de choque em anel).
 * O handler chama um gancho que a parte de cliente preenche — nada de classe de cliente no servidor dedicado.
 */
public final class LegendsBNet {
    /** Feixe: {@code a} → {@code b}, {@code size} = raio do núcleo. */
    public static final byte BEAM = 0;
    /** Corte: centro {@code a}, vetor {@code b} = direção × comprimento, {@code size} = espessura. */
    public static final byte SLASH = 1;
    /** Onda de choque: centro {@code a}, eixo (normal do anel) {@code b}, {@code size} = raio final. */
    public static final byte RING = 2;

    public record LegendFx(byte kind, Vec3 a, Vec3 b, int color, int life, float size) implements CustomPacketPayload {
        public static final Type<LegendFx> TYPE = new Type<>(IsekaiMahou.id("legends_b_fx"));
        public static final StreamCodec<FriendlyByteBuf, LegendFx> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind);
                    buf.writeDouble(p.a.x); buf.writeDouble(p.a.y); buf.writeDouble(p.a.z);
                    buf.writeFloat((float) p.b.x); buf.writeFloat((float) p.b.y); buf.writeFloat((float) p.b.z);
                    buf.writeInt(p.color);
                    buf.writeVarInt(p.life);
                    buf.writeFloat(p.size);
                },
                buf -> new LegendFx(buf.readByte(),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()),
                        buf.readInt(), buf.readVarInt(), buf.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Preenchido pelo LegendsBClient. No servidor dedicado nunca é chamado. */
    public static volatile Consumer<LegendFx> clientFx = p -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("legends_b-1");
        r.playToClient(LegendFx.TYPE, LegendFx.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientFx.accept(p)));
    }

    private LegendsBNet() {}
}
