package com.renanpontes.isekaimahou.feature.settlements.network;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.settlements.SettlementService;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do módulo (registrador "settlements-1"). O estado da cidade vai como NBT (a tela lê com tolerância a campos
 * ausentes). Os ganchos de cliente são trocados pela parte de cliente (servidor dedicado nunca toca neles).
 */
public final class SettlementsNetwork {
    /** Cliente: estado da tela de governo/juramento chegou. */
    public static Consumer<CompoundTag> onState = t -> {};
    /** Cliente: abrir a escolha de estilo da Carta de Fundação. */
    public static Consumer<BlockPos> onFound = p -> {};
    /** Cliente: linhas do rastreador (juramento, obras). */
    public static Consumer<CompoundTag> onTracker = t -> {};

    // ---------------------------------------------------------------- S → C
    public record State(CompoundTag data) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(IsekaiMahou.id("settlements_state"));
        public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, State::data, State::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record FoundOpen(BlockPos pos) implements CustomPacketPayload {
        public static final Type<FoundOpen> TYPE = new Type<>(IsekaiMahou.id("settlements_found_open"));
        public static final StreamCodec<FriendlyByteBuf, FoundOpen> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, FoundOpen::pos, FoundOpen::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Tracker(CompoundTag data) implements CustomPacketPayload {
        public static final Type<Tracker> TYPE = new Type<>(IsekaiMahou.id("settlements_tracker"));
        public static final StreamCodec<FriendlyByteBuf, Tracker> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG, Tracker::data, Tracker::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---------------------------------------------------------------- C → S
    /** Ação no menu da cidade ({@code city} = id do lugar; ação e argumento em texto). */
    public record Action(String city, String action, String arg) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(IsekaiMahou.id("settlements_action"));
        public static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(96), Action::city, ByteBufCodecs.stringUtf8(48), Action::action,
                ByteBufCodecs.stringUtf8(96), Action::arg, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Found(BlockPos pos, String style, String name) implements CustomPacketPayload {
        public static final Type<Found> TYPE = new Type<>(IsekaiMahou.id("settlements_found"));
        public static final StreamCodec<FriendlyByteBuf, Found> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Found::pos, ByteBufCodecs.stringUtf8(16), Found::style,
                ByteBufCodecs.stringUtf8(64), Found::name, Found::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Pede o estado (aba "Cidade" do menu B): {@code city} vazio = a cidade do jogador. */
    public record Request(String city) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(IsekaiMahou.id("settlements_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(96), Request::city, Request::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("settlements-1");
        r.playToClient(State.TYPE, State.CODEC, (p, ctx) -> ctx.enqueueWork(() -> onState.accept(p.data())));
        r.playToClient(FoundOpen.TYPE, FoundOpen.CODEC, (p, ctx) -> ctx.enqueueWork(() -> onFound.accept(p.pos())));
        r.playToClient(Tracker.TYPE, Tracker.CODEC, (p, ctx) -> ctx.enqueueWork(() -> onTracker.accept(p.data())));
        r.playToServer(Action.TYPE, Action.CODEC, (p, ctx) -> server(ctx, sp -> SettlementService.handleAction(sp, p.city(), p.action(), p.arg())));
        r.playToServer(Found.TYPE, Found.CODEC, (p, ctx) -> server(ctx, sp -> {
            SettlementStyle style = SettlementStyle.byId(p.style());
            if (style != null) SettlementService.found(sp, p.pos(), style, p.name());
        }));
        r.playToServer(Request.TYPE, Request.CODEC, (p, ctx) -> server(ctx, sp -> SettlementService.sendState(sp, p.city(), false)));
    }

    private static void server(IPayloadContext ctx, Consumer<ServerPlayer> handler) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) handler.accept(sp);
        });
    }

    private SettlementsNetwork() {}
}
