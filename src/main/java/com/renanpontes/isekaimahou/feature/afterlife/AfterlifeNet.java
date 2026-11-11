package com.renanpontes.isekaimahou.feature.afterlife;

import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do módulo. Os handlers de cliente só tocam classes de cliente dentro do lambda (servidor dedicado seguro). */
public final class AfterlifeNet {

    /** Estado do desafio para a própria alma (servidor → cliente, a cada poucos ticks). */
    public record State(boolean active, double x, double y, double z, int ticksLeft, int totalTicks, int beats, int required,
                        int helpPercent, String helper) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(IsekaiMahou.id("afterlife_state"));
        public static final StreamCodec<FriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of((buf, s) -> {
            buf.writeBoolean(s.active);
            buf.writeDouble(s.x);
            buf.writeDouble(s.y);
            buf.writeDouble(s.z);
            buf.writeVarInt(s.ticksLeft);
            buf.writeVarInt(s.totalTicks);
            buf.writeVarInt(s.beats);
            buf.writeVarInt(s.required);
            buf.writeVarInt(s.helpPercent);
            buf.writeUtf(s.helper, 64);
        }, buf -> new State(buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64)));

        public static final State NONE = new State(false, 0, 0, 0, 0, 1, 0, 1, 0, "");

        @Override public Type<State> type() { return TYPE; }
    }

    /** Quem é alma agora (servidor → quem vê o jogador e ele mesmo): desenhado translúcido. */
    public record Spirit(UUID player, boolean spirit) implements CustomPacketPayload {
        public static final Type<Spirit> TYPE = new Type<>(IsekaiMahou.id("afterlife_spirit"));
        public static final StreamCodec<FriendlyByteBuf, Spirit> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Spirit::player, ByteBufCodecs.BOOL, Spirit::spirit, Spirit::new);

        @Override public Type<Spirit> type() { return TYPE; }
    }

    /** Corpo mais recente do jogador (rastreador + marcador no mundo). {@code has=false} apaga. */
    public record Mark(boolean has, ResourceLocation dim, BlockPos pos, boolean charred, int count) implements CustomPacketPayload {
        public static final Type<Mark> TYPE = new Type<>(IsekaiMahou.id("afterlife_mark"));
        public static final StreamCodec<FriendlyByteBuf, Mark> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Mark::has, ResourceLocation.STREAM_CODEC, Mark::dim, BlockPos.STREAM_CODEC, Mark::pos,
                ByteBufCodecs.BOOL, Mark::charred, ByteBufCodecs.VAR_INT, Mark::count, Mark::new);

        public static final Mark NONE = new Mark(false, ResourceLocation.withDefaultNamespace("overworld"), BlockPos.ZERO, false, 0);

        @Override public Type<Mark> type() { return TYPE; }
    }

    /** Batida do coração apertada pela alma (cliente → servidor): acertou o ritmo ou errou. */
    public record Beat(boolean hit) implements CustomPacketPayload {
        public static final Type<Beat> TYPE = new Type<>(IsekaiMahou.id("afterlife_beat"));
        public static final StreamCodec<FriendlyByteBuf, Beat> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Beat::hit, Beat::new);

        @Override public Type<Beat> type() { return TYPE; }
    }

    /** A alma desistiu (cliente → servidor). */
    public record GiveUp() implements CustomPacketPayload {
        public static final GiveUp INSTANCE = new GiveUp();
        public static final Type<GiveUp> TYPE = new Type<>(IsekaiMahou.id("afterlife_give_up"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GiveUp> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override public Type<GiveUp> type() { return TYPE; }
    }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> {
            PayloadRegistrar r = e.registrar("afterlife-1");
            r.playToClient(State.TYPE, State.STREAM_CODEC, (p, c) -> c.enqueueWork(
                    () -> com.renanpontes.isekaimahou.feature.afterlife.client.ClientAfterlife.onState(p)));
            r.playToClient(Spirit.TYPE, Spirit.STREAM_CODEC, (p, c) -> c.enqueueWork(
                    () -> com.renanpontes.isekaimahou.feature.afterlife.client.ClientAfterlife.onSpirit(p)));
            r.playToClient(Mark.TYPE, Mark.STREAM_CODEC, (p, c) -> c.enqueueWork(
                    () -> com.renanpontes.isekaimahou.feature.afterlife.client.ClientAfterlife.onMark(p)));
            r.playToServer(Beat.TYPE, Beat.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> {
                if (c.player() instanceof ServerPlayer sp) AfterlifeManager.onBeat(sp, p.hit());
            }));
            r.playToServer(GiveUp.TYPE, GiveUp.STREAM_CODEC, (p, c) -> handleGiveUp(c));
        });
    }

    private static void handleGiveUp(IPayloadContext c) {
        c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) AfterlifeManager.onGiveUp(sp);
        });
    }

    private AfterlifeNet() {}
}
