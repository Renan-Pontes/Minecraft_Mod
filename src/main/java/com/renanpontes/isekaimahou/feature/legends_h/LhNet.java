package com.renanpontes.isekaimahou.feature.legends_h;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede do legends_h (servidor → cliente): um pacote só, {@link Screen}, que liga um efeito de tela/estado por
 * {@code ticks} (0 = desliga). Os efeitos de partícula usam o {@code ElementFx} do núcleo.
 */
public final class LhNet {
    /** King Crimson: a entidade {@code entity} está fora do tempo (o cliente não a desenha; se for você, véu vermelho). */
    public static final byte PHASE = 0;
    /** Seu tempo foi apagado (você é o alvo): véu vermelho-roxo com rastros, sem travar o controle. */
    public static final byte ERASED = 1;
    /** O tempo "pulou" perto de você: um corte rápido (clarão vermelho). */
    public static final byte SKIP = 2;
    /** Bites the Dust: seu tempo voltou — sépia e relógio girando ao contrário. */
    public static final byte REWIND = 3;

    public record Screen(byte kind, int entity, int ticks) implements CustomPacketPayload {
        public static final Type<Screen> TYPE = new Type<>(IsekaiMahou.id("legends_h_screen"));
        public static final StreamCodec<FriendlyByteBuf, Screen> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind);
                    buf.writeVarInt(p.entity);
                    buf.writeVarInt(Math.max(0, p.ticks));
                },
                buf -> new Screen(buf.readByte(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Preenchido pelo LegendsHClient. No servidor dedicado nunca é chamado. */
    public static volatile Consumer<Screen> clientScreen = p -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("legends_h-1");
        r.playToClient(Screen.TYPE, Screen.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientScreen.accept(p)));
    }

    private LhNet() {}
}
