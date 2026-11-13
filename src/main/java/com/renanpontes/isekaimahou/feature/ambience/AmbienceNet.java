package com.renanpontes.isekaimahou.feature.ambience;

import com.renanpontes.isekaimahou.IsekaiMahou;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Pacotes do módulo (registrador {@code "ambience-1"}, ids {@code isekai_mahou:ambience_*}). Só servidor → cliente. */
public final class AmbienceNet {
    /** A zona detectada pelo servidor mudou. */
    public static final byte MODE_DETECTED = 0;
    /** Teste por comando: toque esta faixa até segunda ordem ("" = silêncio forçado). */
    public static final byte MODE_FORCED = 1;
    /** Teste por comando: volte ao automático. */
    public static final byte MODE_AUTO = 2;

    /** S→C: zona de música do jogador ("" = nenhuma). */
    public record Zone(String zone, byte mode) implements CustomPacketPayload {
        public static final Type<Zone> TYPE = new Type<>(IsekaiMahou.id("ambience_zone"));
        public static final StreamCodec<ByteBuf, Zone> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32), Zone::zone, ByteBufCodecs.BYTE, Zone::mode, Zone::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * S→C: toque um som de magia. Vai num pacote próprio (e não por {@code level.playSound}) para o volume e o
     * liga/desliga serem de cada jogador.
     */
    public record Sfx(byte kind, byte family, byte size, float x, float y, float z, float pitch) implements CustomPacketPayload {
        public static final Type<Sfx> TYPE = new Type<>(IsekaiMahou.id("ambience_sfx"));
        public static final StreamCodec<ByteBuf, Sfx> STREAM_CODEC = StreamCodec.of((buf, p) -> {
            buf.writeByte(p.kind);
            buf.writeByte(p.family);
            buf.writeByte(p.size);
            buf.writeFloat(p.x);
            buf.writeFloat(p.y);
            buf.writeFloat(p.z);
            buf.writeFloat(p.pitch);
        }, buf -> new Sfx(buf.readByte(), buf.readByte(), buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("ambience-1");
        // o código de cliente só é tocado dentro do lambda (servidor dedicado nunca carrega as classes)
        r.playToClient(Zone.TYPE, Zone.STREAM_CODEC, (p, c) -> c.enqueueWork(() ->
                com.renanpontes.isekaimahou.feature.ambience.client.AmbientPlayer.receive(p)));
        r.playToClient(Sfx.TYPE, Sfx.STREAM_CODEC, (p, c) -> c.enqueueWork(() ->
                com.renanpontes.isekaimahou.feature.ambience.client.SpellSfxClient.receive(p)));
    }

    private AmbienceNet() {}
}
