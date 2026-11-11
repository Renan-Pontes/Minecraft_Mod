package com.renanpontes.isekaimahou.feature.legends_d;

import java.util.function.Consumer;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Rede do módulo legends_d (tudo servidor → cliente). Os handlers chamam ganchos que a parte de cliente preenche:
 * nenhuma classe de cliente é tocada no servidor dedicado.
 */
public final class LdNet {
    /** Letreiro "Eu... sou..." letra a letra: {@code entityId} = quem conjura, {@code ticks} = duração da carga. */
    public static final byte ATOMIC_TEXT = 0;
    /** Detonação do Atômico: "ATÔMICO!" + clarão branco para quem viu. {@code pos} = centro, {@code size} = raio. */
    public static final byte ATOMIC_FLASH = 1;
    /** Carga do Atômico contrariada: o letreiro some. */
    public static final byte ATOMIC_ABORT = 2;
    /** Pilar da Desintegração: clarão dourado curto para quem está perto. */
    public static final byte HOLY_FLASH = 3;
    /**
     * Aviso "Abriguem-se!" para quem está perto de uma carga do Atômico e VAI ficar na bolha protetora:
     * {@code pos} = centro, {@code size} = raio, {@code entityId} = quem conjura, {@code ticks} = quanto falta.
     */
    public static final byte ATOMIC_WARN_SAFE = 4;
    /** Igual ao {@link #ATOMIC_WARN_SAFE}, mas para quem NÃO é aliado (tem que fugir do raio). */
    public static final byte ATOMIC_WARN_DANGER = 5;
    /** Bolha protetora azul em volta de {@code entityId} por {@code ticks} ({@code size} = raio da bolha). */
    public static final byte ATOMIC_BUBBLE = 6;
    /** A bolha de {@code entityId} estourou antes da hora (pousou em chão firme). */
    public static final byte ATOMIC_BUBBLE_END = 7;

    public record Fx(byte kind, Vec3 pos, float size, int entityId, int ticks) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(IsekaiMahou.id("legends_d_fx"));
        public static final StreamCodec<FriendlyByteBuf, Fx> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind);
                    buf.writeDouble(p.pos.x); buf.writeDouble(p.pos.y); buf.writeDouble(p.pos.z);
                    buf.writeFloat(p.size);
                    buf.writeVarInt(p.entityId);
                    buf.writeVarInt(p.ticks);
                },
                buf -> new Fx(buf.readByte(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        buf.readFloat(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Estado de uma parada do tempo: centro/raio, quanto já passou e quanto falta, quem conjurou e quem continua se
     * mexendo (conjurador, aliados, projéteis recém-lançados, chefes que se soltaram). Mandado no início, quando a
     * lista muda e a cada 10 ticks.
     */
    public record TimeStopSync(int zone, Vec3 center, float radius, int elapsed, int remaining, int casterId, int[] exempt)
            implements CustomPacketPayload {
        public static final Type<TimeStopSync> TYPE = new Type<>(IsekaiMahou.id("legends_d_time_stop"));
        public static final StreamCodec<FriendlyByteBuf, TimeStopSync> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.zone);
                    buf.writeDouble(p.center.x); buf.writeDouble(p.center.y); buf.writeDouble(p.center.z);
                    buf.writeFloat(p.radius);
                    buf.writeVarInt(p.elapsed);
                    buf.writeVarInt(p.remaining);
                    buf.writeVarInt(p.casterId);
                    buf.writeVarIntArray(p.exempt);
                },
                buf -> new TimeStopSync(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarIntArray(4096)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** O tempo voltou a andar nesta parada. */
    public record TimeStopEnd(int zone) implements CustomPacketPayload {
        public static final Type<TimeStopEnd> TYPE = new Type<>(IsekaiMahou.id("legends_d_time_resume"));
        public static final StreamCodec<FriendlyByteBuf, TimeStopEnd> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> buf.writeVarInt(p.zone), buf -> new TimeStopEnd(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Jogador amaldiçoado como cabra por {@code ticks} (0 = acabou). */
    public record GoatCurse(int entityId, int ticks) implements CustomPacketPayload {
        public static final Type<GoatCurse> TYPE = new Type<>(IsekaiMahou.id("legends_d_goat_curse"));
        public static final StreamCodec<FriendlyByteBuf, GoatCurse> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeVarInt(p.entityId); buf.writeVarInt(p.ticks); },
                buf -> new GoatCurse(buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // Ganchos preenchidos pelo LegendsDClient (no servidor dedicado nunca são chamados)
    public static volatile Consumer<Fx> clientFx = p -> {};
    public static volatile Consumer<TimeStopSync> clientTimeStop = p -> {};
    public static volatile Consumer<TimeStopEnd> clientTimeResume = p -> {};
    public static volatile Consumer<GoatCurse> clientGoat = p -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("legends_d-1");
        r.playToClient(Fx.TYPE, Fx.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientFx.accept(p)));
        r.playToClient(TimeStopSync.TYPE, TimeStopSync.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientTimeStop.accept(p)));
        r.playToClient(TimeStopEnd.TYPE, TimeStopEnd.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientTimeResume.accept(p)));
        r.playToClient(GoatCurse.TYPE, GoatCurse.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientGoat.accept(p)));
    }

    /** Manda para todos os jogadores do nível a até {@code range} blocos de {@code pos}. */
    public static void near(ServerLevel level, Vec3 pos, double range, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, range, payload);
    }

    public static void to(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    private LdNet() {}
}
