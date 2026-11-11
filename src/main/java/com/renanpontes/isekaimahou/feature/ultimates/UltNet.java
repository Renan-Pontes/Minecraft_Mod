package com.renanpontes.isekaimahou.feature.ultimates;

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
 * Rede do módulo (tudo servidor → cliente, só visual). Os handlers chamam ganchos que a parte de cliente preenche:
 * nada de classe de cliente no servidor dedicado.
 */
public final class UltNet {
    /** Fx: corte em arco do Colosso (centro, yaw, raio). */
    public static final byte FX_SLASH = 0;
    /** Fx: onda de choque em anel no chão (meteoro, pisão). */
    public static final byte FX_RING = 1;
    /** Fx: clarão esférico (impacto do meteoro). */
    public static final byte FX_FLASH = 2;

    /** Presságio da Chuva de Meteoros: céu escurece/avermelha perto do centro; {@code ticks} 0 = acabou (contrariada). */
    public record SkyOmen(int id, Vec3 center, float radius, float height, int ticks, int rgb) implements CustomPacketPayload {
        public static final Type<SkyOmen> TYPE = new Type<>(IsekaiMahou.id("ultimates_sky_omen"));
        public static final StreamCodec<FriendlyByteBuf, SkyOmen> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeDouble(p.center.x); buf.writeDouble(p.center.y); buf.writeDouble(p.center.z);
                    buf.writeFloat(p.radius);
                    buf.writeFloat(p.height);
                    buf.writeVarInt(p.ticks);
                    buf.writeInt(p.rgb);
                },
                buf -> new SkyOmen(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Transcendência ligada/desligada numa entidade (auréola, asas). */
    public record TranscendSync(int entityId, boolean active, int rgb, int ticks) implements CustomPacketPayload {
        public static final Type<TranscendSync> TYPE = new Type<>(IsekaiMahou.id("ultimates_transcend"));
        public static final StreamCodec<FriendlyByteBuf, TranscendSync> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.entityId);
                    buf.writeBoolean(p.active);
                    buf.writeInt(p.rgb);
                    buf.writeVarInt(p.ticks);
                },
                buf -> new TranscendSync(buf.readVarInt(), buf.readBoolean(), buf.readInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Efeito curto desenhado no mundo: corte em arco, anel de choque, clarão. */
    public record Fx(byte kind, Vec3 pos, float yaw, float radius, int rgb, int life) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(IsekaiMahou.id("ultimates_fx"));
        public static final StreamCodec<FriendlyByteBuf, Fx> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.kind);
                    buf.writeDouble(p.pos.x); buf.writeDouble(p.pos.y); buf.writeDouble(p.pos.z);
                    buf.writeFloat(p.yaw);
                    buf.writeFloat(p.radius);
                    buf.writeInt(p.rgb);
                    buf.writeVarInt(p.life);
                },
                buf -> new Fx(buf.readByte(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Ganchos preenchidos pelo UltimatesClient. No servidor dedicado nunca são chamados. */
    public static volatile Consumer<SkyOmen> clientOmen = p -> {};
    public static volatile Consumer<TranscendSync> clientTranscend = p -> {};
    public static volatile Consumer<Fx> clientFx = p -> {};

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("ultimates-1");
        r.playToClient(SkyOmen.TYPE, SkyOmen.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientOmen.accept(p)));
        r.playToClient(TranscendSync.TYPE, TranscendSync.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientTranscend.accept(p)));
        r.playToClient(Fx.TYPE, Fx.STREAM_CODEC, (p, c) -> c.enqueueWork(() -> clientFx.accept(p)));
    }

    /** Manda um efeito para quem está a até {@code range} blocos. */
    public static void sendNear(ServerLevel level, Vec3 at, double range, CustomPacketPayload payload) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(at) <= range * range) PacketDistributor.sendToPlayer(p, payload);
        }
    }

    public static void fx(ServerLevel level, byte kind, Vec3 pos, float yaw, float radius, int rgb, int life) {
        sendNear(level, pos, UltFx.FAR, new Fx(kind, pos, yaw, radius, rgb, life));
    }

    private UltNet() {}
}
