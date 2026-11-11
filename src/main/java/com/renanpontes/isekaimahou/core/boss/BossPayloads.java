package com.renanpontes.isekaimahou.core.boss;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Pacotes do kit de chefes (todos servidor → cliente). Os handlers chamam código de cliente SÓ dentro do lambda,
 * então o servidor dedicado nunca carrega classes de {@code client/}.
 */
public final class BossPayloads {

    /**
     * Nova telegrafia. {@code kind} = ordinal de {@link Telegraph.Kind}; {@code entityId} ≥ 0 = segue a entidade;
     * {@code above} = topo da faixa de acerto acima do Y (o cliente só "deita" o decalque em superfícies até aí).
     */
    public record Show(int id, int kind, int flags, double ax, double ay, double az, double bx, double by, double bz,
                       float radius, float inner, float yaw, float angle, float width,
                       int warmup, int hold, int rgb, int entityId, int lock, float above) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(IsekaiMahou.id("core_boss_telegraph"));
        public static final StreamCodec<FriendlyByteBuf, Show> STREAM_CODEC = StreamCodec.of(Show::write, Show::read);

        private static void write(FriendlyByteBuf buf, Show p) {
            buf.writeVarInt(p.id);
            buf.writeByte(p.kind);
            buf.writeByte(p.flags);
            buf.writeDouble(p.ax);
            buf.writeDouble(p.ay);
            buf.writeDouble(p.az);
            buf.writeDouble(p.bx);
            buf.writeDouble(p.by);
            buf.writeDouble(p.bz);
            buf.writeFloat(p.radius);
            buf.writeFloat(p.inner);
            buf.writeFloat(p.yaw);
            buf.writeFloat(p.angle);
            buf.writeFloat(p.width);
            buf.writeVarInt(p.warmup);
            buf.writeVarInt(p.hold);
            buf.writeInt(p.rgb);
            buf.writeVarInt(p.entityId + 1);
            buf.writeVarInt(p.lock);
            buf.writeFloat(p.above);
        }

        private static Show read(FriendlyByteBuf buf) {
            return new Show(buf.readVarInt(), buf.readByte(), buf.readByte(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readVarInt() - 1, buf.readVarInt(),
                    buf.readFloat());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Fim antecipado de uma telegrafia: {@code flash} = pisca como se tivesse acertado; senão some suave. */
    public record Cancel(int id, boolean flash) implements CustomPacketPayload {
        public static final Type<Cancel> TYPE = new Type<>(IsekaiMahou.id("core_boss_telegraph_cancel"));
        public static final StreamCodec<FriendlyByteBuf, Cancel> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeVarInt(p.id); buf.writeBoolean(p.flash); },
                buf -> new Cancel(buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** O {@code follow} travou neste ponto (posição exata do servidor). */
    public record Lock(int id, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<Lock> TYPE = new Type<>(IsekaiMahou.id("core_boss_telegraph_lock"));
        public static final StreamCodec<FriendlyByteBuf, Lock> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> { buf.writeVarInt(p.id); buf.writeDouble(p.x); buf.writeDouble(p.y); buf.writeDouble(p.z); },
                buf -> new Lock(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Tremor de câmera centrado em (x,y,z); o cliente reduz pela distância até {@code radius}. */
    public record Shake(double x, double y, double z, float radius, float intensity, int ticks) implements CustomPacketPayload {
        public static final Type<Shake> TYPE = new Type<>(IsekaiMahou.id("core_boss_shake"));
        public static final StreamCodec<FriendlyByteBuf, Shake> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeDouble(p.x);
                    buf.writeDouble(p.y);
                    buf.writeDouble(p.z);
                    buf.writeFloat(p.radius);
                    buf.writeFloat(p.intensity);
                    buf.writeVarInt(p.ticks);
                },
                buf -> new Shake(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("core_boss-1");
        r.playToClient(Show.TYPE, Show.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.boss.TelegraphRenderer.receive(p));
        r.playToClient(Cancel.TYPE, Cancel.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.boss.TelegraphRenderer.cancel(p));
        r.playToClient(Lock.TYPE, Lock.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.boss.TelegraphRenderer.lock(p));
        r.playToClient(Shake.TYPE, Shake.STREAM_CODEC,
                (p, c) -> com.renanpontes.isekaimahou.client.boss.BossCameraShake.receive(p));
    }

    private BossPayloads() {}
}
