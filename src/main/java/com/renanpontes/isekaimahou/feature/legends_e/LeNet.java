package com.renanpontes.isekaimahou.feature.legends_e;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede do módulo: um pacote servidor → cliente com efeitos curtos desenhados de verdade (lança de luz que cai do céu,
 * meteoro em brasa, onda de choque em anel e clarão na tela). O handler chama um gancho que a parte de cliente
 * preenche — nenhuma classe de cliente no servidor dedicado.
 */
public final class LeNet {
    /** Lança de luz: cai de {@code a} até {@code a+b} em {@code life} ticks; {@code size} = espessura. */
    public static final byte LANCE = 0;
    /** Meteoro: rocha em brasa de {@code a} até {@code a+b} em {@code life} ticks; {@code size} = raio da rocha. */
    public static final byte METEOR = 1;
    /** Onda de choque em anel: centro {@code a}, eixo {@code b}, {@code size} = raio final. */
    public static final byte RING = 2;
    /** Clarão na tela inteira de quem recebe (cor, {@code life} ticks, {@code size} = intensidade 0..1). */
    public static final byte FLASH = 3;

    public record Fx(byte kind, Vec3 a, Vec3 b, int color, int life, float size) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(IsekaiMahou.id("legends_e_fx"));
        public static final StreamCodec<FriendlyByteBuf, Fx> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind);
                    buf.writeDouble(p.a.x); buf.writeDouble(p.a.y); buf.writeDouble(p.a.z);
                    buf.writeFloat((float) p.b.x); buf.writeFloat((float) p.b.y); buf.writeFloat((float) p.b.z);
                    buf.writeInt(p.color);
                    buf.writeVarInt(p.life);
                    buf.writeFloat(p.size);
                },
                buf -> new Fx(buf.readByte(),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()),
                        buf.readInt(), buf.readVarInt(), buf.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Preenchido pelo LegendsEClient. No servidor dedicado nunca é chamado. */
    public static volatile Consumer<Fx> clientFx = p -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("legends_e-1");
        r.playToClient(Fx.TYPE, Fx.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientFx.accept(p)));
    }

    private LeNet() {}
}
