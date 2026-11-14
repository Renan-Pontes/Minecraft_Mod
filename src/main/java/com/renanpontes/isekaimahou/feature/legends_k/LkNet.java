package com.renanpontes.isekaimahou.feature.legends_k;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede do legends_k (servidor → cliente): um pacote só, {@link Screen}, que liga um efeito de tela por {@code ticks}
 * (0 = desliga). Só quem está no efeito recebe — nada de poluir a tela dos outros.
 */
public final class LkNet {
    /** Dentro da dimensão do Kamui: a tela fica azulada. */
    public static final byte KAMUI = 0;
    /** Entre dimensões (D4C): azul-claro. */
    public static final byte D4C = 1;
    /** Retorno pela Morte: a tela apaga e volta. */
    public static final byte RBD = 2;

    public record Screen(byte kind, int ticks) implements CustomPacketPayload {
        public static final Type<Screen> TYPE = new Type<>(IsekaiMahou.id("legends_k_screen"));
        public static final StreamCodec<FriendlyByteBuf, Screen> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind);
                    buf.writeVarInt(Math.max(0, p.ticks));
                },
                buf -> new Screen(buf.readByte(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Preenchido pelo LegendsKClient. No servidor dedicado nunca é chamado. */
    public static volatile Consumer<Screen> clientScreen = p -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("legends_k-1");
        r.playToClient(Screen.TYPE, Screen.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientScreen.accept(p)));
    }

    public static void screen(ServerPlayer sp, byte kind, int ticks) {
        if (sp instanceof FakePlayer || sp.connection == null) return;
        PacketDistributor.sendToPlayer(sp, new Screen(kind, ticks));
    }

    private LkNet() {}
}
