package com.renanpontes.isekaimahou.feature.legends_c;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede do módulo: um pacote servidor → cliente com efeitos curtos desenhados de verdade (cortes do Desmantelar, grade do
 * Clivar, onda do Vermelho, blocos sugados pelo Azul, clarões, ondulações no Infinito). O handler chama um gancho que a
 * parte de cliente preenche — nada de classe de cliente no servidor dedicado.
 */
public final class LcNet {
    /** Corte reto: {@code a} → {@code b} (pontos absolutos), {@code size} = altura da lâmina do corte. */
    public static final byte SLASH = 0;
    /** Grade de cortes sobre um alvo: centro {@code a}, {@code b} = (meia largura, meia altura, giro), {@code extra} = nº de cortes. */
    public static final byte GRID = 1;
    /** Onda de choque esférica: centro {@code a}, eixo {@code b}, {@code size} = raio final. */
    public static final byte WAVE = 2;
    /** Bloco sugado: de {@code a} até {@code b}, {@code extra} = id do BlockState. */
    public static final byte SUCK = 3;
    /** Clarão: centro {@code a}, {@code size} = raio. */
    public static final byte FLASH = 4;
    /** Ondulação na casca do Infinito: ponto {@code a}, normal {@code b}, {@code size} = raio. */
    public static final byte RIPPLE = 5;

    public record Fx(byte kind, Vec3 a, Vec3 b, int color, int life, float size, int extra) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(IsekaiMahou.id("legends_c_fx"));
        public static final StreamCodec<FriendlyByteBuf, Fx> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind);
                    buf.writeDouble(p.a.x); buf.writeDouble(p.a.y); buf.writeDouble(p.a.z);
                    buf.writeDouble(p.b.x); buf.writeDouble(p.b.y); buf.writeDouble(p.b.z);
                    buf.writeInt(p.color);
                    buf.writeVarInt(p.life);
                    buf.writeFloat(p.size);
                    buf.writeVarInt(p.extra);
                },
                buf -> new Fx(buf.readByte(),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        buf.readInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Preenchido pelo LegendsCClient. No servidor dedicado nunca é chamado. */
    public static volatile Consumer<Fx> clientFx = p -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("legends_c-1");
        r.playToClient(Fx.TYPE, Fx.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientFx.accept(p)));
    }

    private LcNet() {}
}
