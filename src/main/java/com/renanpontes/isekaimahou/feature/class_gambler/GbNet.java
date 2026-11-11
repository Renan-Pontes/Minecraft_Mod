package com.renanpontes.isekaimahou.feature.class_gambler;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.class_gambler.rank.HouseChallenge;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do Apostador. Servidor → cliente: resultado da rolagem (HUD), estado do Jackpot e da Rodada da Casa.
 * Cliente → servidor: começar a Rodada da Casa (botão da aba). Os ganchos de cliente são preenchidos pelo
 * {@code ClassGamblerClient}; no servidor dedicado ficam nulos e nada de cliente é carregado.
 */
public final class GbNet {
    public static volatile Consumer<RollShown> onRoll;
    public static volatile Consumer<JackpotState> onJackpot;
    public static volatile Consumer<ChallengeState> onChallenge;

    /** Resultado de uma rolagem: faixa, variação (%), visual, Tensão atual e se quem rolou é Apostador. */
    public record RollShown(byte band, int percent, byte kind, byte fever, boolean gambler) implements CustomPacketPayload {
        public static final Type<RollShown> TYPE = new Type<>(IsekaiMahou.id("class_gambler_roll"));
        public static final StreamCodec<ByteBuf, RollShown> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BYTE, RollShown::band, ByteBufCodecs.VAR_INT, RollShown::percent, ByteBufCodecs.BYTE, RollShown::kind,
                ByteBufCodecs.BYTE, RollShown::fever, ByteBufCodecs.BOOL, RollShown::gambler, RollShown::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Jackpot: ticks restantes e total (0 = acabou). */
    public record JackpotState(int ticks, int total) implements CustomPacketPayload {
        public static final Type<JackpotState> TYPE = new Type<>(IsekaiMahou.id("class_gambler_jackpot"));
        public static final StreamCodec<ByteBuf, JackpotState> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, JackpotState::ticks, ByteBufCodecs.VAR_INT, JackpotState::total, JackpotState::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Rodada da Casa: abates, meta, ticks restantes, rank em jogo e estado (0 rodando, 1 venceu, 2 perdeu). */
    public record ChallengeState(int kills, int target, int ticks, int rank, byte result) implements CustomPacketPayload {
        public static final Type<ChallengeState> TYPE = new Type<>(IsekaiMahou.id("class_gambler_challenge"));
        public static final StreamCodec<ByteBuf, ChallengeState> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ChallengeState::kills, ByteBufCodecs.VAR_INT, ChallengeState::target,
                ByteBufCodecs.VAR_INT, ChallengeState::ticks, ByteBufCodecs.VAR_INT, ChallengeState::rank,
                ByteBufCodecs.BYTE, ChallengeState::result, ChallengeState::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Cliente pede para começar a Rodada da Casa do próximo rank. */
    public record StartChallenge() implements CustomPacketPayload {
        public static final StartChallenge INSTANCE = new StartChallenge();
        public static final Type<StartChallenge> TYPE = new Type<>(IsekaiMahou.id("class_gambler_start"));
        public static final StreamCodec<ByteBuf, StartChallenge> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("class_gambler-1");
        r.playToClient(RollShown.TYPE, RollShown.STREAM_CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            Consumer<RollShown> h = onRoll;
            if (h != null) h.accept(msg);
        }));
        r.playToClient(JackpotState.TYPE, JackpotState.STREAM_CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            Consumer<JackpotState> h = onJackpot;
            if (h != null) h.accept(msg);
        }));
        r.playToClient(ChallengeState.TYPE, ChallengeState.STREAM_CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            Consumer<ChallengeState> h = onChallenge;
            if (h != null) h.accept(msg);
        }));
        r.playToServer(StartChallenge.TYPE, StartChallenge.STREAM_CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) HouseChallenge.start(sp);
        }));
    }

    private GbNet() {}
}
