package com.renanpontes.isekaimahou.feature.class_quincy;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes da aba Quincy (C→S): subir de rank e escolher a Letra. O servidor confere tudo de novo. */
public final class QuincyNetwork {
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("class_quincy-1");
        r.playToServer(RankUp.TYPE, RankUp.STREAM_CODEC, (p, c) -> {
            if (c.player() instanceof ServerPlayer sp) QuincyRanks.tryRankUp(sp);
        });
        r.playToServer(ChooseLetter.TYPE, ChooseLetter.STREAM_CODEC, (p, c) -> {
            if (!(c.player() instanceof ServerPlayer sp)) return;
            Quincy.Letter l = Quincy.Letter.byIndex(p.index());
            if (l != null) QuincyRanks.chooseLetter(sp, l, false);
        });
    }

    /** Pedido de subir para o próximo rank do Auswählen. */
    public record RankUp() implements CustomPacketPayload {
        public static final Type<RankUp> TYPE = new Type<>(IsekaiMahou.id("class_quincy_rank_up"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RankUp> STREAM_CODEC = StreamCodec.unit(new RankUp());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Escolha da Letra (índice de {@link Quincy.Letter}). */
    public record ChooseLetter(int index) implements CustomPacketPayload {
        public static final Type<ChooseLetter> TYPE = new Type<>(IsekaiMahou.id("class_quincy_choose_letter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ChooseLetter> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, ChooseLetter::index, ChooseLetter::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private QuincyNetwork() {}
}
